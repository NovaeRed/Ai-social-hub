package cn.redture.chat.service;

import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.vo.*;
import cn.redture.common.pojo.vo.CursorPageResult;

import java.util.List;

public interface GroupService {

    ConversationCreatedResultVO createGroup(Long currentUserId, String name, List<String> memberPublicIds);

    void batchInsertMembers(Long conversationId, List<Long> userIds);

    Conversation createGroupCore(Long currentUserId, String name);

    ConversationSummaryVO updateGroup(Long currentUserId, String groupPublicId, String name);

    void addGroupMembers(Long currentUserId, String groupPublicId, List<String> userPublicIds);

    List<GroupAdminVO> listGroupAdmins(Long currentUserId, String groupPublicId);

    void addGroupAdmins(Long currentUserId, String groupPublicId, List<String> userPublicIds);

    void removeGroupAdmins(Long currentUserId, String groupPublicId, List<String> userPublicIds);

    void transferGroupOwnership(Long currentUserId, String groupPublicId, String newOwnerPublicId);

    void removeGroupMember(Long currentUserId, String groupPublicId, String userPublicId);

    void removeGroup(Long currentUserId, String groupPublicId);

    void leaveGroup(Long currentUserId, String groupPublicId);

    CursorPageResult<GroupSummaryVO> listGroups(Long currentUserId, Long cursor, int limit, String keyword);

    GroupDetailVO getGroupDetail(Long currentUserId, String groupPublicId);
    
    List<GroupDetailVO.MemberVO> listGroupMembers(Long currentUserId, String groupPublicId, String keyword);
}

