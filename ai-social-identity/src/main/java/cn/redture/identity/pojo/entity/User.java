package cn.redture.identity.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 对应 ai_social.sql 中的 users 表。
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String publicId;

    private String username;

    private String nickname;

    private String avatarUrl;

    private String passwordHash;

    private String email;

    private String phone;

    private String vipLevel;

    private Boolean aiAnalysisEnabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}

