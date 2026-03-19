package cn.redture.aiEngine.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
public class AiProfileVO {
    @JsonProperty("profile_type")
    private String profileType;
    private Object content;
    @JsonProperty("model_name")
    private String modelName;
    private String provider;
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}
