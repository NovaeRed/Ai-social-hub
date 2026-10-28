package cn.redture.chat.service;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.enums.ConversationMemberRoleEnum;
import cn.redture.chat.service.impl.GroupServiceImpl;
import cn.redture.common.exception.businessException.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupAccessAndLifecycleTest {
    GroupServiceImpl service;
    ConversationMapper groups;
    ConversationMemberMapper members;
    Conversation group;

    @BeforeEach void setup() {
        service = new GroupServiceImpl(); groups = mock(ConversationMapper.class); members = mock(ConversationMemberMapper.class);
        ReflectionTestUtils.setField(service, "conversationMapper", groups);
        ReflectionTestUtils.setField(service, "conversationMemberMapper", members);
        group = new Conversation(); group.setId(7L); group.setPublicId("group");
        when(groups.selectOne(any())).thenReturn(group);
    }
    @Test void outsidersCannotReadGroupDetailsOrMembers() {
        assertThrows(AccessDeniedException.class, () -> service.getGroupDetail(8L, "group"));
        assertThrows(AccessDeniedException.class, () -> service.listGroupMembers(8L, "group", null));
        verify(members, never()).selectList(any());
    }
    @Test void outsidersCannotReadAdminIdentities() {
        assertThrows(AccessDeniedException.class, () -> service.listGroupAdmins(8L, "group"));
        verify(members, never()).selectList(any());
    }
    @Test void dissolveKeepsMessageForeignKeysAndArchivesTheGroupAfterRemovingMembership() {
        ConversationMember owner = new ConversationMember(); owner.setRole(ConversationMemberRoleEnum.OWNER);
        when(members.selectByConversationIdAndUserId(7L, 1L)).thenReturn(owner);
        service.removeGroup(1L, "group");
        assertNotNull(group.getDeletedAt());
        InOrder order = inOrder(members, groups);
        order.verify(members).delete(any());
        order.verify(groups).updateById(group);
        verify(groups, never()).deleteById(anyLong());
    }
    @Test void membersCannotDissolveTheGroup() {
        ConversationMember member = new ConversationMember(); member.setRole(ConversationMemberRoleEnum.MEMBER);
        when(members.selectByConversationIdAndUserId(7L, 2L)).thenReturn(member);
        assertThrows(AccessDeniedException.class, () -> service.removeGroup(2L, "group"));
        verify(groups, never()).updateById(any(Conversation.class));
        verify(members, never()).delete(any());
    }
    @Test void createdGroupCountIncludesOwner() {
        Conversation created = service.createGroupCore(1L, "群聊");
        assertEquals(1L, created.getMemberCount());
    }
    @Test void ownerCanPublishAndClearAnnouncement() {
        ConversationMember owner = new ConversationMember(); owner.setRole(ConversationMemberRoleEnum.OWNER);
        when(members.selectByConversationIdAndUserId(7L, 1L)).thenReturn(owner);
        service.updateGroupAnnouncement(1L, "group", "  周五交付\n请提前准备  ");
        assertEquals("周五交付\n请提前准备", group.getAnnouncement());
        service.updateGroupAnnouncement(1L, "group", "");
        assertEquals("", group.getAnnouncement());
        assertThrows(cn.redture.common.exception.businessException.InvalidInputException.class,
                () -> service.updateGroupAnnouncement(1L, "group", "字".repeat(2001)));
        verify(groups, times(2)).updateById(group);
    }
    @Test void membersAdminsAndOutsidersCannotEditAnnouncement() {
        ConversationMember member = new ConversationMember(); member.setRole(ConversationMemberRoleEnum.MEMBER);
        ConversationMember admin = new ConversationMember(); admin.setRole(ConversationMemberRoleEnum.ADMIN);
        when(members.selectByConversationIdAndUserId(7L, 2L)).thenReturn(member, admin, null);
        for (int i = 0; i < 3; i++)
            assertThrows(AccessDeniedException.class, () -> service.updateGroupAnnouncement(2L, "group", "无权修改"));
        verify(groups, never()).updateById(any(Conversation.class));
    }
    @Test void batchInsertionDoesNotPassNullOrDuplicateMembersToSql() {
        ConversationMember existing = new ConversationMember();
        when(members.selectByConversationIdAndUserId(7L, 1L)).thenReturn(existing);
        when(members.insertBatch(anyList())).thenReturn(1);
        service.batchInsertMembers(7L, java.util.Arrays.asList(1L, null, 2L, 2L));
        verify(members).insertBatch(argThat(batch -> batch.size() == 1 && batch.getFirst().getUserId().equals(2L) && batch.getFirst().getRole() == ConversationMemberRoleEnum.MEMBER));
        verify(groups).incrementMemberCount(7L, 1);
    }
}
