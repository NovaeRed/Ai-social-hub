package cn.redture.chat.pojo.vo;

import cn.redture.chat.pojo.enums.ConversationMemberRoleEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class GroupDetailVO {

    @JsonProperty("public_id")
    private String publicId;

    private String name;

    @JsonProperty("member_count")
    private Long memberCount;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    private List<MemberVO> members;

    @Data
    public static class MemberVO {
        @JsonProperty("public_id")
        private String publicId;

        private String nickname;

        private ConversationMemberRoleEnum role;

        @JsonProperty("joined_at")
        private OffsetDateTime joinedAt;
    }
}
