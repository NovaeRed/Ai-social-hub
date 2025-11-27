package cn.redture.identity.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 好友列表中的用户摘要信息。
 */
@Data
public class FriendSummaryVO {

    @JsonProperty("public_id")
    private String publicId;

    private String nickname;

    @JsonProperty("avatar_url")
    private String avatarUrl;
}
