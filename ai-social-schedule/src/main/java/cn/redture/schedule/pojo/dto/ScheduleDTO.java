package cn.redture.schedule.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 创建/更新日程 DTO
 */
@Data
public class ScheduleDTO {

    private String title;

    private String description;

    @JsonProperty("start_time")
    private OffsetDateTime startTime;

    @JsonProperty("end_time")
    private OffsetDateTime endTime;

    private String location;

    @JsonProperty("is_ai_extracted")
    private Boolean isAiExtracted;

    @JsonProperty("source_message_id")
    private Long sourceMessageId;
}
