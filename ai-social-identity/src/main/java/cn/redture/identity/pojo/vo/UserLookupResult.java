package cn.redture.identity.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 用于核对好友申请接收人的最小公开信息。 */
public record UserLookupResult(@JsonProperty("public_id") String publicId,
                               String username,
                               String nickname,
                               @JsonProperty("avatar_url") String avatarUrl) {
}
