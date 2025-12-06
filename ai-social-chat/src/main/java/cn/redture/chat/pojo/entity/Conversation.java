package cn.redture.chat.pojo.entity;

import cn.redture.chat.pojo.enums.ConversationTypeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.redture.common.annotation.PgEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对应 ai_social.sql 中的 conversations 表。
 */
@Data
@TableName("conversations")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty("public_id")
    private String publicId;

    private ConversationTypeEnum type; // PRIVATE / GROUP

    private String name;

    @JsonProperty("member_count")
    private Long memberCount;

    @JsonProperty("latest_message_id")
    private Long latestMessageId;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("deleted_at")
    private OffsetDateTime deletedAt;
}
