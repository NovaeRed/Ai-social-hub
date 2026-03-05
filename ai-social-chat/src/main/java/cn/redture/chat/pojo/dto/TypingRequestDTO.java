package cn.redture.chat.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TypingRequestDTO {

    @JsonProperty("conversation_public_id")
    private String conversationPublicId;

    @JsonProperty("target_user_public_id")
    private String targetUserPublicId;
}
