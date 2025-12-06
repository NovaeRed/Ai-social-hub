package cn.redture.chat.service;

public interface TypingService {
    void reportTyping(String conversationPublicId, Long currentUserId);

    void stopTyping(String conversationPublicId, Long currentUserId);
}
