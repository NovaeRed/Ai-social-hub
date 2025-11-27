package cn.redture.identity.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

import cn.redture.identity.pojo.enums.FriendshipRequestStatus;

/**
 * 对应 ai_social.sql 中的 friendship_requests 表。
 */
@Data
@TableName("friendship_requests")
public class FriendshipRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String publicId;

    private Long senderId;

    private Long receiverId;

    private String message;

    /**
     * 状态：PENDING / ACCEPTED / REJECTED
     */
    private FriendshipRequestStatus status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
