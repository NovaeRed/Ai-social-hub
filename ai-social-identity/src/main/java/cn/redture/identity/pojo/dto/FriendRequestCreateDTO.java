package cn.redture.identity.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 发送好友请求 DTO。
 */
@Data
public class FriendRequestCreateDTO {

    /**
     * 目标用户的 public_id。
     */
    @JsonProperty("target_user_public_id")
    private String targetUserPublicId;

    private String message;
}
