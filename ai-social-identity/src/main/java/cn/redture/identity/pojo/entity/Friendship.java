package cn.redture.identity.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对应 ai_social.sql 中的 friendships 表。
 */
@Data
@TableName("friendships")
public class Friendship {

    @TableField("user_id_1")
    private Long userId1;

    @TableField("user_id_2")
    private Long userId2;

    private OffsetDateTime createdAt;
}
