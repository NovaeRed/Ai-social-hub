package cn.redture.chat.pojo.vo;

import cn.redture.chat.pojo.enums.MediaTypeEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MessageItemVO {

    @JsonProperty("public_id")
    private String publicId;

    private SenderVO sender;

    private String content;

    @JsonProperty("media_type")
    private MediaTypeEnum mediaType;

    @JsonProperty("media_url")
    private String mediaUrl;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @Data
    public static class SenderVO {
        @JsonProperty("public_id")
        private String publicId;

        private String nickname;
    }
}
