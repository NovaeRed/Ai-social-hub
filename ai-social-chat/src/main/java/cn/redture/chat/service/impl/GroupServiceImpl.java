package cn.redture.chat.service.impl;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.enums.ConversationMemberRoleEnum;
import cn.redture.chat.pojo.enums.ConversationTypeEnum;
import cn.redture.chat.pojo.vo.*;
import cn.redture.chat.service.GroupService;
import cn.redture.chat.util.converter.ConversationConverter;
import cn.redture.common.exception.businessException.AccessDeniedException;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.common.util.IdUtil;
import cn.redture.identity.pojo.vo.UserInformation;
import cn.redture.identity.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupServiceImpl implements GroupService {

    @Resource
    private ConversationMapper conversationMapper;

    @Resource
    private ConversationMemberMapper conversationMemberMapper;

    @Resource
    private UserService userService;

    /**
     * 创建群聊
     *
     * @param currentUserId   当前用户ID
     * @param name            群名称
     * @param memberPublicIds 初始成员Public ID列表
     * @return 创建结果
     */
    @Override
    public ConversationCreatedResultVO createGroup(Long currentUserId, String name, List<String> memberPublicIds) {
        // 步骤 1: 创建群 + 添加创建者（原子操作）
        GroupService proxy = (GroupService) AopContext.currentProxy();
        Conversation group = proxy.createGroupCore(currentUserId, name);

        // 步骤 2: 批量解析并添加其他成员（非原子，可部分失败）
        if (memberPublicIds != null && !memberPublicIds.isEmpty()) {
            // 批量查询 publicId → userId
            List<Long> validMemberUserIds = resolvePublicIdsToUserIds(memberPublicIds);

            if (!validMemberUserIds.isEmpty()) {
                // 批量插入成员（单次事务）
                proxy.batchInsertMembers(group.getId(), validMemberUserIds);
            }
        }
        return ConversationConverter.INSTANCE.toConversationCreatedResultVO(group);
    }

    @Transactional
    @Override
    public Conversation createGroupCore(Long currentUserId, String name) {
        Conversation group = new Conversation();
        group.setPublicId(IdUtil.nextId());
        group.setType(ConversationTypeEnum.GROUP);
        group.setName(name);
        group.setMemberCount(1L);
        group.setCreatedAt(OffsetDateTime.now());
        conversationMapper.insert(group);

        ConversationMember owner = new ConversationMember();
        owner.setConversationId(group.getId());
        owner.setUserId(currentUserId);
        owner.setRole(ConversationMemberRoleEnum.OWNER);
        owner.setJoinedAt(OffsetDateTime.now());
        conversationMemberMapper.insert(owner);

        return group;
    }

    private List<Long> resolvePublicIdsToUserIds(List<String> publicIds) {
        return userService.getUserIdsByPublicIds(publicIds);
    }

    @Transactional
    @Override
    public void batchInsertMembers(Long conversationId, List<Long> userIds) {
        List<ConversationMember> members = userIds.stream().filter(Objects::nonNull).distinct().map(userId -> {
            if (conversationMemberMapper.selectByConversationIdAndUserId(conversationId, userId) != null) {
                // 已经是成员，跳过
                return null;
            }

            ConversationMember m = new ConversationMember();
            m.setConversationId(conversationId);
            m.setUserId(userId);
            m.setRole(ConversationMemberRoleEnum.MEMBER);
            m.setJoinedAt(OffsetDateTime.now());
            return m;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        if (members.isEmpty()) return;

        int newMemberCount = conversationMemberMapper.insertBatch(members);
        conversationMapper.incrementMemberCount(conversationId, newMemberCount);
    }

    /**
     * 更新群信息
     *
     * @param currentUserId 当前用户ID
     * @param groupPublicId 群组Public ID
     * @param name          新的群名称
     * @return 更新后的群信息
     */
    @Override
    @Transactional
    public ConversationSummaryVO updateGroup(Long currentUserId, String groupPublicId, String name) {
        Conversation group = getConversationByPublicId(groupPublicId);

        if (conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId) == null) {
            throw new AccessDeniedException("不是群组成员，无法修改群信息");
        }

        group.setName(name);
        conversationMapper.updateById(group);
        return ConversationConverter.INSTANCE.toConversationSummaryVO(group);
    }

    @Override
    @Transactional
    public void updateGroupAnnouncement(Long currentUserId, String groupPublicId, String announcement) {
        Conversation group = getConversationByPublicId(groupPublicId);
        ConversationMember operator = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId);
        if (operator == null || operator.getRole() != ConversationMemberRoleEnum.OWNER) {
            throw new AccessDeniedException("只有群主才能编辑群公告");
        }
        if (announcement == null || announcement.length() > 2000) {
            throw new InvalidInputException("公告内容不能为空值且不能超过 2000 字");
        }
        group.setAnnouncement(announcement.trim());
        conversationMapper.updateById(group);
    }

    /**
     * 添加群成员
     *
     * @param currentUserId 当前用户ID
     * @param groupPublicId 群组Public ID
     * @param userPublicIds 需要添加的用户Public ID列表
     */
    // TODO 大事务拆分
    @Override
    @Transactional
    public void addGroupMembers(Long currentUserId, String groupPublicId, List<String> userPublicIds) {
        if (groupPublicId == null) {
            throw new InvalidInputException("群组 public_id 不能为空");
        }

        Conversation group = getConversationByPublicId(groupPublicId);

        if (conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId) == null) {
            throw new AccessDeniedException("不是群组成员，无法添加新成员");
        }

        List<Long> userIds = userService.getUserIdsByPublicIds(userPublicIds);
        List<ConversationMember> membersToInsert = new ArrayList<>();

        for (Long userId : userIds.stream().distinct().toList()) {
            if (userId == null) {
                continue;
            }

            if (conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), userId) != null) {
                // 已经是成员，跳过
                continue;
            }

            ConversationMember member = new ConversationMember();
            member.setConversationId(group.getId());
            member.setUserId(userId);
            member.setRole(ConversationMemberRoleEnum.MEMBER);
            member.setJoinedAt(OffsetDateTime.now());
            membersToInsert.add(member);
        }

        if (!membersToInsert.isEmpty()) {
            conversationMemberMapper.insertBatch(membersToInsert);
            // 更新群成员数
            conversationMapper.refreshMemberCount(group.getId());
        }
    }

    /**
     * 列出群管理员
     *
     * @param currentUserId 当前用户ID
     * @param groupPublicId 群组Public ID
     * @return 管理员列表
     */
    @Override
    public List<GroupAdminVO> listGroupAdmins(Long currentUserId, String groupPublicId) {
        Conversation group = getConversationByPublicId(groupPublicId);
        requireMember(group.getId(), currentUserId);
        List<ConversationMember> adminMembers = conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, group.getId())
                .in(ConversationMember::getRole, Arrays.asList(ConversationMemberRoleEnum.OWNER, ConversationMemberRoleEnum.ADMIN)));
        List<GroupAdminVO> adminVOs = new ArrayList<>();
        for (ConversationMember member : adminMembers) {
            UserInformation userInformation = userService.getUserById(member.getUserId());
            GroupAdminVO vo = new GroupAdminVO();
            vo.setPublicId(userInformation.getPublicId());
            vo.setNickname(userInformation.getNickname());
            vo.setAvatarUrl(userInformation.getAvatarUrl());
            adminVOs.add(vo);
        }

        return adminVOs;
    }

    /**
     * 添加群管理员
     *
     * @param currentUserId 当前用户ID
     * @param groupPublicId 群组Public ID
     * @param userPublicIds 需要添加为管理员的用户Public ID列表
     */
    @Override
    public void addGroupAdmins(Long currentUserId, String groupPublicId, List<String> userPublicIds) {
        Conversation group = getConversationByPublicId(groupPublicId);
        ConversationMember currentConversationMember = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId);
        if (currentConversationMember == null || currentConversationMember.getRole() != ConversationMemberRoleEnum.OWNER) {
            throw new AccessDeniedException("只有群主才能添加管理员");
        }

        List<Long> userIds = userService.getUserIdsByPublicIds(userPublicIds);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        List<Long> validUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (validUserIds.isEmpty()) {
            throw new ResourceNotFoundException("用户");
        }

        List<ConversationMember> targetMembers = conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, group.getId())
                .in(ConversationMember::getUserId, validUserIds));

        Set<Long> memberUserIds = targetMembers.stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        // 检查是否有输入用户不在群聊中
        List<Long> notInGroupUserIds = validUserIds.stream()
                .filter(userId -> !memberUserIds.contains(userId))
                .toList();
        if (!notInGroupUserIds.isEmpty()) {
            throw new InvalidInputException("存在不在群聊中的用户，无法设置为管理员");
        }

        Set<Long> existingAdminIds = targetMembers.stream()
                .filter(member -> member.getRole() == ConversationMemberRoleEnum.ADMIN)
                .map(ConversationMember::getUserId)
                .collect(Collectors.toSet());

        List<Long> membersToUpdateIds = validUserIds.stream()
                .filter(userId -> !existingAdminIds.contains(userId))
                .collect(Collectors.toList());

        if (!membersToUpdateIds.isEmpty()) {
            conversationMemberMapper.batchUpdateRoleToAdmin(group.getId(), membersToUpdateIds);
        }
    }

    /**
     * 移除群管理员
     *
     * @param currentUserId 当前用户ID
     * @param groupPublicId 群组Public ID
     * @param userPublicIds 需要移除管理员身份的用户Public ID列表
     */
    @Override
    public void removeGroupAdmins(Long currentUserId, String groupPublicId, List<String> userPublicIds) {
        Conversation group = getConversationByPublicId(groupPublicId);
        ConversationMember currentConversationMember = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId);
        if (currentConversationMember == null || currentConversationMember.getRole() != ConversationMemberRoleEnum.OWNER) {
            throw new AccessDeniedException("只有群主才能移除管理员");
        }

        List<Long> membersToUpdateIds = new ArrayList<>();
        userService.getUserIdsByPublicIds(userPublicIds).forEach(userId -> {
            ConversationMember targetUserMember = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), userId);
            if (targetUserMember == null || targetUserMember.getRole() != ConversationMemberRoleEnum.ADMIN) {
                return;
            }
            membersToUpdateIds.add(userId);
        });
        if (!membersToUpdateIds.isEmpty())
            conversationMemberMapper.batchUpdateRoleToMember(group.getId(), membersToUpdateIds);
    }

    /**
     * 转让群主
     *
     * @param currentUserId    当前用户ID
     * @param groupPublicId    群组Public ID
     * @param newOwnerPublicId 新群主的用户Public ID
     */
    @Override
    @Transactional
    public void transferGroupOwnership(Long currentUserId, String groupPublicId, String newOwnerPublicId) {
        Conversation group = getConversationByPublicId(groupPublicId);
        ConversationMember currentConversationMember = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId);
        if (currentConversationMember == null || currentConversationMember.getRole() != ConversationMemberRoleEnum.OWNER) {
            throw new AccessDeniedException("只有群主才能转让群主身份");
        }
        List<Long> userIds = userService.getUserIdsByPublicIds(Collections.singletonList(newOwnerPublicId));
        if (userIds.isEmpty() || userIds.getFirst() == null) {
            throw new ResourceNotFoundException("用户");
        }
        ConversationMember newOwnerMember = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), userIds.getFirst());
        if (newOwnerMember == null) {
            throw new ResourceNotFoundException("群组成员");
        }

        conversationMemberMapper.updateRoleToOwner(group.getId(), userIds.getFirst());
        conversationMemberMapper.batchUpdateRoleToMember(group.getId(), Collections.singletonList(currentUserId));
    }

    /**
     * 移除群成员
     *
     * @param currentUserId 操作者用户ID
     * @param groupPublicId 群组Public ID
     * @param userPublicId  目标用户Public ID
     */
    @Override
    @Transactional
    public void removeGroupMember(Long currentUserId, String groupPublicId, String userPublicId) {
        // 步骤 1. 查询群组信息
        Conversation group = getConversationByPublicId(groupPublicId);

        // 步骤 2. 查询用户ID
        List<Long> userIds = userService.getUserIdsByPublicIds(Collections.singletonList(userPublicId));
        if (userIds.isEmpty() || userIds.getFirst() == null) {
            throw new ResourceNotFoundException("用户");
        }
        Long targetUserId = userIds.getFirst();

        // 步骤 3. 查询所有需要的成员信息
        List<ConversationMember> members = conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, group.getId())
                .in(ConversationMember::getUserId, Arrays.asList(currentUserId, targetUserId)));
        if (Objects.equals(currentUserId, targetUserId)) {
            throw new InvalidInputException("不能移除自己");
        }

        // 步骤 4. 从查询结果中获取成员信息
        ConversationMember operatorMember = null;
        ConversationMember targetMember = null;

        for (ConversationMember member : members) {
            if (member.getUserId().equals(currentUserId)) {
                operatorMember = member;
            } else if (member.getUserId().equals(targetUserId)) {
                targetMember = member;
            }
        }

        // 步骤 5. 权限检查
        validatePermission(operatorMember, targetMember, targetUserId);

        // 步骤 6. 执行删除操作
        int deletedCount = conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, group.getId())
                .eq(ConversationMember::getUserId, targetUserId));

        if (deletedCount > 0) {
            conversationMapper.refreshMemberCount(group.getId());
        }
    }

    /**
     * 解散群聊
     *
     * @param currentUserId 群主ID
     * @param groupPublicId 群组Public ID
     */
    @Override
    @Transactional
    public void removeGroup(Long currentUserId, String groupPublicId) {
        Conversation group = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, groupPublicId)
                .isNull(Conversation::getDeletedAt)
                .eq(Conversation::getType, ConversationTypeEnum.GROUP));

        if (group == null) {
            throw new ResourceNotFoundException("群组");
        }

        ConversationMember operatorMember = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId);
        if (operatorMember == null || operatorMember.getRole() != ConversationMemberRoleEnum.OWNER) {
            throw new AccessDeniedException("只有群主才能解散群聊");
        }

        conversationMemberMapper.delete(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, group.getId()));

        // 保留消息及其外键关联；已解散的群聊
        // 会从所有对外会话读写入口中排除。
        group.setDeletedAt(OffsetDateTime.now());
        conversationMapper.updateById(group);
    }

    @Override
    @Transactional
    public void leaveGroup(Long currentUserId, String groupPublicId) {
        Conversation group = getConversationByPublicId(groupPublicId);
        ConversationMember operatorMember = conversationMemberMapper.selectByConversationIdAndUserId(group.getId(), currentUserId);
        if (operatorMember == null) {
            throw new ResourceNotFoundException("群组成员");
        }

        if (operatorMember.getRole() == ConversationMemberRoleEnum.OWNER) {
            throw new InvalidInputException("群主不能退出群聊，请解散群聊或转让群主身份");
        }

        int success = conversationMemberMapper.deleteById(operatorMember.getId());
        if (success == 1) {
            conversationMapper.refreshMemberCount(group.getId());
        }
    }

    /**
     * 验证权限和业务规则
     */
    private void validatePermission(ConversationMember operatorMember, ConversationMember targetMember, Long targetUserId) {
        if (operatorMember == null) {
            throw new AccessDeniedException("不是群组成员，无法执行此操作");
        }

        if (targetMember == null) {
            throw new ResourceNotFoundException("群组成员");
        }

        // 检查操作权限：不能移除群主
        if (targetMember.getRole() == ConversationMemberRoleEnum.OWNER) {
            throw new InvalidInputException("不能移除群主");
        }

        // 检查操作者权限：普通成员无权移除任何人
        if (operatorMember.getRole() == ConversationMemberRoleEnum.MEMBER) {
            throw new AccessDeniedException("没有权限移除成员");
        }

        // 检查操作者角色：群主可以移除任何人，管理员只能移除普通成员
        if (operatorMember.getRole() == ConversationMemberRoleEnum.ADMIN &&
                targetMember.getRole() != ConversationMemberRoleEnum.MEMBER) {
            throw new AccessDeniedException("管理员只能移除普通成员");
        }

        // 不能移除自己
        Long currentUserId = operatorMember.getUserId();
        if (currentUserId.equals(targetUserId)) {
            throw new InvalidInputException("不能移除自己");
        }
    }

    @Override
    public CursorPageResult<GroupSummaryVO> listGroups(Long currentUserId, Long cursor, int limit, String keyword) {
        if (currentUserId == null) {
            return new CursorPageResult<>();
        }

        limit = Math.min(limit, 100);
        List<Conversation> groups = conversationMapper.selectUserGroupsByCursor(currentUserId, cursor, limit + 1, keyword);

        boolean hasMore = groups.size() > limit;
        if (hasMore) {
            groups = groups.subList(0, limit);
        }

        List<GroupSummaryVO> items = groups.stream().map(group -> {
            GroupSummaryVO vo = new GroupSummaryVO();
            vo.setPublicId(group.getPublicId());
            vo.setName(group.getName());
            vo.setCreatedAt(group.getCreatedAt());
            vo.setMemberCount(group.getMemberCount());
            return vo;
        }).collect(Collectors.toList());

        CursorPageResult<GroupSummaryVO> result = new CursorPageResult<>();
        result.setItems(items);
        result.setHasMore(hasMore);

        if (hasMore && !groups.isEmpty()) {
            result.setNextCursor(groups.getLast().getId());
        } else {
            result.setNextCursor(null);
        }

        return result;
    }

    @Override
    public GroupDetailVO getGroupDetail(Long currentUserId, String groupPublicId) {
        Conversation group = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, groupPublicId)
                .isNull(Conversation::getDeletedAt)
                .eq(Conversation::getType, ConversationTypeEnum.GROUP));
        if (group == null) {
            throw new ResourceNotFoundException("群组");
        }
        requireMember(group.getId(), currentUserId);

        List<ConversationMember> members = conversationMemberMapper.selectList(new LambdaQueryWrapper<ConversationMember>()
                .eq(ConversationMember::getConversationId, group.getId()));

        GroupDetailVO detail = new GroupDetailVO();
        detail.setPublicId(group.getPublicId());
        detail.setName(group.getName());
        detail.setAnnouncement(group.getAnnouncement());
        detail.setCreatedAt(group.getCreatedAt());
        detail.setMemberCount((long) members.size());

        List<GroupDetailVO.MemberVO> memberVOList = members.stream().map(cm -> {
            UserInformation userInformation = userService.getUserById(cm.getUserId());

            GroupDetailVO.MemberVO vo = new GroupDetailVO.MemberVO();
            vo.setJoinedAt(cm.getJoinedAt());
            vo.setRole(cm.getRole());
            vo.setPublicId(userInformation.getPublicId());
            vo.setNickname(userInformation.getNickname());
            return vo;
        }).toList();
        detail.setMembers(memberVOList);

        return detail;
    }

    @Override
    public List<GroupDetailVO.MemberVO> listGroupMembers(Long currentUserId, String groupPublicId, String keyword) {
        GroupDetailVO detail = getGroupDetail(currentUserId, groupPublicId);
        List<GroupDetailVO.MemberVO> members = detail.getMembers();
        if (StringUtils.hasText(keyword)) {
            members = members.stream()
                    .filter(m -> m.getNickname() != null && m.getNickname().contains(keyword))
                    .collect(Collectors.toList());
        }
        return members;
    }

    private Conversation getConversationByPublicId(String groupPublicId) {
        Conversation group = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getPublicId, groupPublicId)
                .isNull(Conversation::getDeletedAt)
                .eq(Conversation::getType, ConversationTypeEnum.GROUP)
                .last("FOR UPDATE"));
        if (group == null) {
            throw new ResourceNotFoundException("群组");
        }
        return group;
    }

    private void requireMember(Long conversationId, Long userId) {
        if (userId == null || conversationMemberMapper.selectByConversationIdAndUserId(conversationId, userId) == null)
            throw new AccessDeniedException("不是群组成员，无法查看群资料");
    }
}

