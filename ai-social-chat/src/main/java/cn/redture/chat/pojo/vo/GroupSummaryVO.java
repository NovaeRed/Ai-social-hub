package cn.redture.chat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class GroupSummaryVO {

    @JsonProperty("public_id")
    private String publicId;

    private String name;

    @JsonProperty("member_count")
    private Long memberCount;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
}
