package cn.redture.chat.pojo.entity;

import cn.redture.chat.pojo.enums.ConversationMemberRoleEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对应 ai_social.sql 中的 conversation_members 表。
 */
@Data
@TableName("conversation_members")
public class ConversationMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long userId;

    private OffsetDateTime joinedAt;

    private ConversationMemberRoleEnum role;

    private Long lastReadMessageId;

    private OffsetDateTime lastReadAt;

    private Long clearedMessageId;
}
