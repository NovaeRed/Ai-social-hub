package cn.redture.identity.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 单条好友请求视图。
 */
@Data
public class FriendRequestItemVO {

    @JsonProperty("request_public_id")
    private String requestPublicId;

    private String message;

    private String status;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    private SimpleUserVO sender;

    @Data
    public static class SimpleUserVO {
        @JsonProperty("public_id")
        private String publicId;
        private String nickname;
    }
}
