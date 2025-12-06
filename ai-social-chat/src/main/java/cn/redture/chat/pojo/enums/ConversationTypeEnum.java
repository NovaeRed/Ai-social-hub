package cn.redture.chat.pojo.enums;

import cn.redture.common.annotation.PgEnum;

@PgEnum("conversation_type_enum")
public enum ConversationTypeEnum {
    PRIVATE,
    GROUP
}
