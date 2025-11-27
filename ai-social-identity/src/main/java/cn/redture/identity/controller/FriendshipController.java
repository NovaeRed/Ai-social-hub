package cn.redture.identity.controller;

import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import cn.redture.identity.pojo.dto.FriendRequestCreateDTO;
import cn.redture.identity.pojo.vo.FriendRequestListVO;
import cn.redture.identity.pojo.vo.FriendSummaryVO;
import cn.redture.identity.service.FriendshipService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class FriendshipController {

    @Resource
    private FriendshipService friendshipService;

    /**
     * 获取好友列表
     */
    @GetMapping("/friends")
    public RestResult<List<FriendSummaryVO>> getFriends() {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        List<FriendSummaryVO> friends = friendshipService.listFriends(currentUserId);
        return RestResult.success(friends);
    }

    /**
     *  发送好友请求
     */
    @PostMapping("/friends/requests")
    public RestResult<Void> sendFriendRequest(@RequestBody FriendRequestCreateDTO dto) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        friendshipService.sendFriendRequest(currentUserId, dto);
        return RestResult.noContent();
    }

    /**
     * 获取好友请求列表
     */
    @GetMapping("/friends/requests")
    public RestResult<FriendRequestListVO> listFriendRequests() {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        FriendRequestListVO vo = friendshipService.listFriendRequests(currentUserId);
        return RestResult.success(vo);
    }

    /**
     * 接受好友请求
     */
    @PostMapping("/friends/requests/{requestPublicId}/accept")
    public RestResult<Void> acceptFriendRequest(@PathVariable("requestPublicId") String requestPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        friendshipService.acceptFriendRequest(currentUserId, requestPublicId);
        return RestResult.noContent();
    }

    /**
     * 取消或拒绝好友请求
     */
    @DeleteMapping("/friends/requests/{requestPublicId}")
    public RestResult<Void> cancelOrRejectFriendRequest(@PathVariable("requestPublicId") String requestPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        friendshipService.cancelOrRejectFriendRequest(currentUserId, requestPublicId);
        return RestResult.noContent();
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/friends/{friendPublicId}")
    public RestResult<Void> deleteFriend(@PathVariable("friendPublicId") String friendPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        friendshipService.deleteFriend(currentUserId, friendPublicId);
        return RestResult.noContent();
    }
}
