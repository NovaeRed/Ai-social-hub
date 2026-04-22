package cn.redture.chat.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FileMetadataBatchQueryDTO {

    @JsonProperty("file_public_ids")
    private List<String> filePublicIds;
}
