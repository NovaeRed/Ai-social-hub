package cn.redture.chat.service;

import cn.redture.chat.pojo.dto.TypingRequestDTO;

public interface TypingService {
    void reportTyping(Long currentUserId, TypingRequestDTO typingRequestDTO1);

    void stopTyping(Long currentUserId, TypingRequestDTO typingRequestDTO);
}
