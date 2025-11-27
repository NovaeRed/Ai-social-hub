package cn.redture.identity.service.impl;

import cn.redture.common.exception.businessException.FriendshipBusinessException;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.util.IdUtil;
import cn.redture.identity.mapper.FriendshipMapper;
import cn.redture.identity.mapper.FriendshipRequestMapper;
import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.pojo.enums.FriendshipRequestStatus;
import cn.redture.identity.pojo.dto.FriendRequestCreateDTO;
import cn.redture.identity.pojo.entity.Friendship;
import cn.redture.identity.pojo.entity.FriendshipRequest;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.vo.FriendRequestItemVO;
import cn.redture.identity.pojo.vo.FriendRequestListVO;
import cn.redture.identity.pojo.vo.FriendSummaryVO;
import cn.redture.identity.service.FriendshipService;
import cn.redture.identity.util.converter.FriendshipConverter;
import cn.redture.identity.util.converter.UserConverter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FriendshipServiceImpl implements FriendshipService {

    @Resource
    private FriendshipRequestMapper friendshipRequestMapper;

    @Resource
    private FriendshipMapper friendshipMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public List<FriendSummaryVO> listFriends(Long currentUserId) {
        // 查询当前用户作为 user_id_1 或 user_id_2 的所有好友关系
        LambdaQueryWrapper<Friendship> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friendship::getUserId1, currentUserId)
                .or()
                .eq(Friendship::getUserId2, currentUserId);
        List<Friendship> friendships = friendshipMapper.selectList(wrapper);
        if (friendships.isEmpty()) {
            return List.of();
        }
        // 提取好友的 userId 列表
        List<Long> friendUserIds = friendships.stream()
                .map(f -> Objects.equals(f.getUserId1(), currentUserId) ? f.getUserId2() : f.getUserId1())
                .distinct()
                .toList();

        if (friendUserIds.isEmpty()) {
            return List.of();
        }

        List<User> users = userMapper.selectByIds(friendUserIds);
        return users.stream().map(UserConverter.INSTANCE::toFriendSummaryVO).toList();
    }

    @Override
    @Transactional
    public void sendFriendRequest(Long currentUserId, FriendRequestCreateDTO dto) {
        if (dto == null || dto.getTargetUserPublicId() == null) {
            throw new FriendshipBusinessException("目标用户不能为空");
        }
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new ResourceNotFoundException("当前用户");
        }
        User targetUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPublicId, dto.getTargetUserPublicId()));
        if (targetUser == null) {
            throw new FriendshipBusinessException("目标用户不存在");
        }
        if (Objects.equals(currentUser.getId(), targetUser.getId())) {
            throw new FriendshipBusinessException("不能添加自己为好友");
        }
        if (isAlreadyFriends(currentUser.getId(), targetUser.getId())) {
            throw new FriendshipBusinessException("你们已经是好友");
        }
        // 检查是否已有请求记录（利用唯一约束 sender_id + receiver_id）
        FriendshipRequest existing = friendshipRequestMapper.selectOne(new LambdaQueryWrapper<FriendshipRequest>()
                .eq(FriendshipRequest::getSenderId, currentUser.getId())
                .eq(FriendshipRequest::getReceiverId, targetUser.getId()));
        if (existing != null) {
            if (existing.getStatus() == FriendshipRequestStatus.PENDING) {
                // 幂等：已有待处理请求则直接返回
                return;
            }
            if (existing.getStatus() == FriendshipRequestStatus.ACCEPTED) {
                throw new FriendshipBusinessException("你们已经是好友");
            }
            // 若之前对方拒绝过（REJECTED），重新发起请求：复用旧记录，重置为 PENDING 并更新消息
            existing.setStatus(FriendshipRequestStatus.PENDING);
            existing.setMessage(dto.getMessage());
            friendshipRequestMapper.updateById(existing);
            return;
        }
        // 创建新请求
        FriendshipRequest request = new FriendshipRequest();
        request.setPublicId(IdUtil.nextId());
        request.setSenderId(currentUser.getId());
        request.setReceiverId(targetUser.getId());
        request.setMessage(dto.getMessage());
        request.setStatus(FriendshipRequestStatus.PENDING);
        friendshipRequestMapper.insert(request);
    }

    @Override
    public FriendRequestListVO listFriendRequests(Long currentUserId) {
        // incoming：当前用户是接收者
        List<FriendRequestItemVO> incoming = friendshipRequestMapper.selectList(new LambdaQueryWrapper<FriendshipRequest>()
                        .eq(FriendshipRequest::getReceiverId, currentUserId))
                .stream()
                .map(req -> toFriendRequestItemVO(req, true))
                .sorted(Comparator.comparing(FriendRequestItemVO::getCreatedAt).reversed())
                .collect(Collectors.toList());

        // outgoing：当前用户是发送者
        List<FriendRequestItemVO> outgoing = friendshipRequestMapper.selectList(new LambdaQueryWrapper<FriendshipRequest>()
                        .eq(FriendshipRequest::getSenderId, currentUserId))
                .stream()
                .map(req -> toFriendRequestItemVO(req, false))
                .sorted(Comparator.comparing(FriendRequestItemVO::getCreatedAt).reversed())
                .collect(Collectors.toList());

        FriendRequestListVO vo = new FriendRequestListVO();
        vo.setIncoming(incoming);
        vo.setOutgoing(outgoing);
        return vo;
    }

    @Override
    @Transactional
    public void acceptFriendRequest(Long currentUserId, String requestPublicId) {
        FriendshipRequest request = loadRequestByPublicId(requestPublicId);
        if (!Objects.equals(request.getReceiverId(), currentUserId)) {
            throw new FriendshipBusinessException("无权接受该好友请求");
        }
        if (request.getStatus() != FriendshipRequestStatus.PENDING) {
            // 非 PENDING 状态下，视为幂等，不再重复处理
            return;
        }
        // 更新请求状态为 ACCEPTED
        request.setStatus(FriendshipRequestStatus.ACCEPTED);
        friendshipRequestMapper.updateById(request);

        // 创建好友关系（按 userId1 < userId2 存储）
        Long userId1 = Math.min(request.getSenderId(), request.getReceiverId());
        Long userId2 = Math.max(request.getSenderId(), request.getReceiverId());

        if (!isAlreadyFriends(userId1, userId2)) {
            Friendship friendship = new Friendship();
            friendship.setUserId1(userId1);
            friendship.setUserId2(userId2);
            friendshipMapper.insert(friendship);
        }
    }

    @Override
    @Transactional
    public void cancelOrRejectFriendRequest(Long currentUserId, String requestPublicId) {
        FriendshipRequest request = loadRequestByPublicId(requestPublicId);
        if (request.getStatus() != FriendshipRequestStatus.PENDING) {
            return;
        }
        if (!Objects.equals(request.getSenderId(), currentUserId) && !Objects.equals(request.getReceiverId(), currentUserId)) {
            throw new FriendshipBusinessException("无权操作该好友请求");
        }

        // 发送者：撤销请求 -> 直接删除记录
        if (Objects.equals(request.getSenderId(), currentUserId)) {
            friendshipRequestMapper.deleteById(request.getId());
            return;
        }

        // 接收者：拒绝请求 -> 修改状态为 REJECTED
        request.setStatus(FriendshipRequestStatus.REJECTED);
        friendshipRequestMapper.updateById(request);
    }

    @Override
    @Transactional
    public void deleteFriend(Long currentUserId, String friendPublicId) {
        User friend = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPublicId, friendPublicId));
        if (friend == null) {
            throw new ResourceNotFoundException("好友");
        }
        Long userId1 = Math.min(currentUserId, friend.getId());
        Long userId2 = Math.max(currentUserId, friend.getId());
        friendshipMapper.delete(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getUserId1, userId1)
                .eq(Friendship::getUserId2, userId2));
    }

    private boolean isAlreadyFriends(Long userId1, Long userId2) {
        Long minId = Math.min(userId1, userId2);
        Long maxId = Math.max(userId1, userId2);
        Long count = friendshipMapper.selectCount(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getUserId1, minId)
                .eq(Friendship::getUserId2, maxId));
        return count != null && count > 0;
    }

    private FriendshipRequest loadRequestByPublicId(String requestPublicId) {
        FriendshipRequest request = friendshipRequestMapper.selectOne(new LambdaQueryWrapper<FriendshipRequest>()
                .eq(FriendshipRequest::getPublicId, requestPublicId));
        if (request == null) {
            throw new ResourceNotFoundException("好友请求");
        }
        return request;
    }

    private FriendRequestItemVO toFriendRequestItemVO(FriendshipRequest req, boolean incoming) {
        FriendRequestItemVO vo = new FriendRequestItemVO();
        vo.setRequestPublicId(req.getPublicId());
        vo.setMessage(req.getMessage());
        vo.setStatus(String.valueOf(req.getStatus()));
        vo.setCreatedAt(req.getCreatedAt());

        FriendRequestItemVO.SimpleUserVO senderVo = new FriendRequestItemVO.SimpleUserVO();
        User sender = userMapper.selectById(req.getSenderId());
        if (sender != null) {
            senderVo.setPublicId(sender.getPublicId());
            senderVo.setNickname(sender.getNickname());
        }
        vo.setSender(senderVo);
        return vo;
    }
}
