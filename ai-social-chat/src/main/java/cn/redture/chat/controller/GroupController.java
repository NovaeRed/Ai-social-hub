package cn.redture.chat.controller;

import cn.redture.chat.pojo.dto.AddMembersRequestDTO;
import cn.redture.chat.pojo.dto.CreateGroupRequestDTO;
import cn.redture.chat.pojo.dto.NewOwnerRequestDTO;
import cn.redture.chat.pojo.dto.UpdateGroupRequestDTO;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.vo.*;
import cn.redture.chat.service.GroupService;
import cn.redture.common.exception.businessException.InvalidInputException;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.pojo.vo.CursorPageResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/groups")
public class GroupController {

    @Resource
    private GroupService groupService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public RestResult<ConversationCreatedResultVO> createGroup(@RequestBody CreateGroupRequestDTO request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new InvalidInputException("群名称不能为空");
        }
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        ConversationCreatedResultVO group = groupService.createGroup(currentUserId, request.getName(), request.getMemberPublicIds());
        return RestResult.created(group);
    }

    // TODO 更改通知公告等功能
    @PatchMapping("/{group_public_id}")
    public RestResult<ConversationSummaryVO> updateGroup(@PathVariable("group_public_id") String groupPublicId,
                                                         @RequestBody UpdateGroupRequestDTO request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new InvalidInputException("群名称不能为空");
        }
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        ConversationSummaryVO group = groupService.updateGroup(currentUserId, groupPublicId, request.getName());
        return RestResult.success(group);
    }

    @PostMapping("/{group_public_id}/members")
    public RestResult<Void> addMembers(@PathVariable("group_public_id") String groupPublicId,
                                       @RequestBody AddMembersRequestDTO request) {
        if (request == null || request.getUserPublicIds() == null || request.getUserPublicIds().isEmpty()) {
            throw new InvalidInputException("user_public_ids 不能为空");
        }
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        groupService.addGroupMembers(currentUserId, groupPublicId, request.getUserPublicIds());
        return RestResult.noContent();
    }

    @GetMapping("/{group_public_id}/members")
    public RestResult<List<GroupDetailVO.MemberVO>> listGroupMembers(@PathVariable("group_public_id") String groupPublicId,
                                                            @RequestParam(value = "keyword", required = false) String keyword) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        List<GroupDetailVO.MemberVO> members = groupService.listGroupMembers(currentUserId, groupPublicId, keyword);
        return RestResult.success(members);
    }

    @GetMapping("/{group_public_id}/members/admins")
    public RestResult<List<GroupAdminVO>> listGroupAdmins(@PathVariable("group_public_id") String groupPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        List<GroupAdminVO> admins = groupService.listGroupAdmins(currentUserId, groupPublicId);
        return RestResult.success(admins);
    }

    @PostMapping("/{group_public_id}/members/admins")
    public RestResult<Void> addGroupAdmins(@PathVariable("group_public_id") String groupPublicId,
                                           @RequestBody AddMembersRequestDTO request) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        groupService.addGroupAdmins(currentUserId, groupPublicId, request.getUserPublicIds());
        return RestResult.noContent();
    }

    @DeleteMapping("/{group_public_id}/members/admins")
    public RestResult<Void> removeGroupAdmins(@PathVariable("group_public_id") String groupPublicId,
                                              @RequestBody AddMembersRequestDTO request) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        groupService.removeGroupAdmins(currentUserId, groupPublicId, request.getUserPublicIds());
        return RestResult.noContent();
    }

    @PostMapping("/{group_public_id}/members/owner")
    public RestResult<Void> transferGroupOwnership(@PathVariable("group_public_id") String groupPublicId,
                                                   @RequestBody NewOwnerRequestDTO request) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        groupService.transferGroupOwnership(currentUserId, groupPublicId, request.getNewOwnerPublicId());
        return RestResult.noContent();
    }

    @DeleteMapping("/{group_public_id}/members/{user_public_id}")
    public RestResult<Void> removeMember(@PathVariable("group_public_id") String groupPublicId,
                                         @PathVariable("user_public_id") String userPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        groupService.removeGroupMember(currentUserId, groupPublicId, userPublicId);
        return RestResult.noContent();
    }

    @DeleteMapping("/{group_public_id}/members/owner")
    public RestResult<Void> removeGroup(@PathVariable("group_public_id") String groupPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        groupService.removeGroup(currentUserId, groupPublicId);
        return RestResult.noContent();
    }

    @DeleteMapping("/{group_public_id}/members/me")
    public RestResult<Void> leaveGroup(@PathVariable("group_public_id") String groupPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        groupService.leaveGroup(currentUserId, groupPublicId);
        return RestResult.noContent();
    }

    @GetMapping
    public RestResult<CursorPageResult<GroupSummaryVO>> listGroups(@RequestParam(value = "cursor", required = false) Long cursor,
                                                                   @RequestParam(value = "limit", defaultValue = "20") int limit,
                                                                   @RequestParam(value = "keyword", required = false) String keyword) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        CursorPageResult<GroupSummaryVO> page = groupService.listGroups(currentUserId, cursor, limit, keyword);
        return RestResult.success(page);
    }

    @GetMapping("/{group_public_id}")
    public RestResult<GroupDetailVO> getGroupDetail(@PathVariable("group_public_id") String groupPublicId) {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        GroupDetailVO detail = groupService.getGroupDetail(currentUserId, groupPublicId);
        return RestResult.success(detail);
    }
}

