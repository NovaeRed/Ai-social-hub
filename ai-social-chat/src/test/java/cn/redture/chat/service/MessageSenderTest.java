package cn.redture.chat.service;

import cn.redture.chat.mapper.ConversationMapper;
import cn.redture.chat.mapper.ConversationMemberMapper;
import cn.redture.chat.mapper.MessageMapper;
import cn.redture.chat.pojo.dto.CreateMessageDTO;
import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.entity.ConversationMember;
import cn.redture.chat.pojo.entity.Message;
import cn.redture.chat.pojo.vo.MessageItemVO;
import cn.redture.chat.service.impl.MessageServiceImpl;
import cn.redture.chat.sse.SseEmitterService;
import cn.redture.chat.sse.Notification;
import cn.redture.identity.mapper.UserMapper;
import cn.redture.identity.pojo.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageSenderTest {
    @Test
    void historyResolvesDistinctSendersInOneQueryWithoutExposingPrivateFields() {
        MessageServiceImpl service = new MessageServiceImpl();
        MessageMapper messages = mock(MessageMapper.class);
        UserMapper users = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "messageMapper", messages);
        ReflectionTestUtils.setField(service, "userMapper", users);
        when(messages.selectRecentMessages(7L, 50)).thenReturn(List.of(message(1L), message(2L), message(1L)));
        User first = user(1L, "林沐");
        first.setPasswordHash("private-hash");
        when(users.selectByIds(anyCollection())).thenReturn(List.of(first, user(2L, "陈一诺")));
        List<MessageItemVO> result = service.getRecentContextMessages(7L, 50);
        assertEquals(List.of("林沐", "陈一诺", "林沐"), result.stream().map(item -> item.getSender().getNickname()).toList());
        assertEquals("person-1", result.getFirst().getSender().getPublicId());
        verify(users).selectByIds(List.of(1L, 2L));
    }

    @Test
    void createResponseAndSseContainTheSameSender() {
        MessageServiceImpl service = new MessageServiceImpl();
        MessageMapper messages = mock(MessageMapper.class);
        UserMapper users = mock(UserMapper.class);
        ConversationMapper conversations = mock(ConversationMapper.class);
        ConversationMemberMapper members = mock(ConversationMemberMapper.class);
        SseEmitterService sse = mock(SseEmitterService.class);
        ReflectionTestUtils.setField(service, "messageMapper", messages);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "conversationMapper", conversations);
        ReflectionTestUtils.setField(service, "conversationMemberMapper", members);
        ReflectionTestUtils.setField(service, "sseEmitterService", sse);
        ReflectionTestUtils.setField(service, "eventPublisher", mock(ApplicationEventPublisher.class));
        Conversation conversation = new Conversation(); conversation.setId(7L);
        ConversationMember sender = new ConversationMember(); sender.setUserId(1L);
        ConversationMember recipient = new ConversationMember(); recipient.setUserId(2L);
        when(conversations.selectOne(any())).thenReturn(conversation);
        when(members.selectByConversationIdAndUserId(7L, 1L)).thenReturn(sender);
        when(members.selectList(any())).thenReturn(List.of(sender, recipient));
        when(users.selectByIds(anyCollection())).thenReturn(List.of(user(1L, "林沐")));
        CreateMessageDTO dto = new CreateMessageDTO(); dto.setContent("你好");
        MessageItemVO result = service.createMessage("group", 1L, dto);
        assertEquals("林沐", result.getSender().getNickname());
        verify(sse).sendToUser(eq(2L), argThat((Notification<?> notification) -> notification.getPayload() == result));
    }

    @Test
    void generatedNamesFallBackToUsernameAndDeletedAccountsRemainIdentifiable() {
        MessageServiceImpl service = new MessageServiceImpl();
        MessageMapper messages = mock(MessageMapper.class);
        UserMapper users = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "messageMapper", messages);
        ReflectionTestUtils.setField(service, "userMapper", users);
        when(messages.selectRecentMessages(7L, 50)).thenReturn(List.of(message(1L), message(2L)));
        User first = user(1L, "AIHub_generated"); first.setUsername("linmu");
        User second = user(2L, "旧昵称"); second.setDeletedAt(OffsetDateTime.now());
        when(users.selectByIds(anyCollection())).thenReturn(List.of(first, second));
        List<MessageItemVO> result = service.getRecentContextMessages(7L, 50);
        assertEquals("linmu", result.getFirst().getSender().getNickname());
        assertEquals("已注销用户", result.getLast().getSender().getNickname());
        assertEquals("person-2", result.getLast().getSender().getPublicId());
    }

    private Message message(Long sender) { Message message = new Message(); message.setSenderId(sender); return message; }
    private User user(Long id, String name) { User user = new User(); user.setId(id); user.setPublicId("person-" + id); user.setNickname(name); return user; }
}
