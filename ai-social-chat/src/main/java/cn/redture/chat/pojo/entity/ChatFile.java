package cn.redture.chat.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对应 ai_social.sql 中的 chat_files 表。
 */
@Data
@TableName("chat_files")
public class ChatFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String publicId;

    private Long uploaderId;

    private Long conversationId;

    private String accessUrl;

    private String originalFilename;

    private String fileExt;

    private String contentType;

    private Long sizeBytes;

    private OffsetDateTime createdAt;

    private OffsetDateTime deletedAt;
}
