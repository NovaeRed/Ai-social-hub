package cn.redture.chat.pojo.enums;

import cn.redture.common.annotation.PgEnum;

@PgEnum("media_type_enum")
public enum MediaTypeEnum {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    FILE
}
