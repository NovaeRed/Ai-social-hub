package cn.redture.chat.pojo.enums;

import cn.redture.common.annotation.PgEnum;

@PgEnum("conversation_member_role_enum")
public enum ConversationMemberRoleEnum {
    MEMBER,
    ADMIN,
    OWNER
}
