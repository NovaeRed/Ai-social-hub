package cn.redture.chat.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreatePrivateConversationRequestDTO {

    @JsonProperty("target_user_public_id")
    private String targetUserPublicId;

}