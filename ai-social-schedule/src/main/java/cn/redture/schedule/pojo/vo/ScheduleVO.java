package cn.redture.schedule.pojo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 日程信息 VO
 */
@Data
@Builder
public class ScheduleVO {

    @JsonProperty("public_id")
    private String publicId;

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
