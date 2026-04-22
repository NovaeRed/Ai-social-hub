package cn.redture.chat.pojo.dto;

import cn.redture.chat.pojo.enums.MediaTypeEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateMessageDTO {

    private String content;

    @JsonProperty("media_type")
    private MediaTypeEnum mediaType;

    @JsonProperty("file_public_id")
    private String filePublicId;

    // 客户端可以提供一个临时ID，用于在发送成功后对应上乐观更新的UI项
    @JsonProperty("temp_id")
    private String tempId;
}
