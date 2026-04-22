package cn.redture.chat.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ChatFileVO {

    @JsonProperty("public_id")
    private String publicId;

    @JsonProperty("original_filename")
    private String originalFilename;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @JsonProperty("access_url")
    private String accessUrl;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
}
