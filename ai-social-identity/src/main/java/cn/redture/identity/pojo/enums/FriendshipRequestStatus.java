package cn.redture.identity.pojo.enums;

import cn.redture.common.annotation.PgEnum;

/**
 * 对应 ai_social.sql 中 friendship_request_status_enum。
 */
@PgEnum("friendship_request_status_enum")
public enum FriendshipRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
