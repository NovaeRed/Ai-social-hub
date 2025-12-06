package cn.redture.chat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class GroupAdminVO {

    @JsonProperty("public_id")
    private String publicId;

    private String nickname;

    @JsonProperty("avatar_url")
    private String avatarUrl;
}
