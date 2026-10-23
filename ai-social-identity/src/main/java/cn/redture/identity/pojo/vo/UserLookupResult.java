package cn.redture.identity.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Minimal information for confirming the recipient of a friend request. */
public record UserLookupResult(@JsonProperty("public_id") String publicId,
                               String username,
                               String nickname,
                               @JsonProperty("avatar_url") String avatarUrl) {
}
