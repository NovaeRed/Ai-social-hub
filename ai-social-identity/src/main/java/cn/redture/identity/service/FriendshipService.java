package cn.redture.identity.service;

import cn.redture.identity.pojo.dto.FriendRequestCreateDTO;
import cn.redture.identity.pojo.vo.FriendRequestListVO;
import cn.redture.identity.pojo.vo.FriendSummaryVO;

import java.util.List;

public interface FriendshipService {

    /**
     * 获取当前用户的好友列表。
     */
    List<FriendSummaryVO> listFriends(Long currentUserId, String keyword);

    /**
     * 发送好友请求。
     */
    void sendFriendRequest(Long currentUserId, FriendRequestCreateDTO dto);

    /**
     * 获取当前用户相关的好友请求（incoming / outgoing）。
     */
    FriendRequestListVO listFriendRequests(Long currentUserId, String keyword);

    /**
     * 接受好友请求。
     */
    void acceptFriendRequest(Long currentUserId, String requestPublicId);

    /**
     * 拒绝或撤销好友请求。
     */
    void cancelOrRejectFriendRequest(Long currentUserId, String requestPublicId);

    /**
     * 删除好友。
     */
    void deleteFriend(Long currentUserId, String friendPublicId);
}
