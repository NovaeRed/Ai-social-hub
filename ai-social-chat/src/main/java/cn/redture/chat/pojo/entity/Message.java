package cn.redture.chat.pojo.entity;

import cn.redture.chat.pojo.enums.MediaTypeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.redture.common.annotation.PgEnum;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对应 ai_social.sql 中的 messages 表。
 */
@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String publicId;

    private Long conversationId;

    private Long senderId;

    private String content;

    private MediaTypeEnum mediaType;

    private String mediaUrl;

    private String sourceType;

    private OffsetDateTime createdAt;

    private OffsetDateTime deletedAt;
}
