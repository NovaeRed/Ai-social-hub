package cn.redture.chat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ConversationCreatedResultVO {

    @JsonProperty("public_id")
    private String publicId;
}
