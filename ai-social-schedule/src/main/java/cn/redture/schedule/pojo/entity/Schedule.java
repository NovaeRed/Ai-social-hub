package cn.redture.schedule.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 日程事件实体
 */
@Data
@TableName("schedules")
public class Schedule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String publicId;

    private Long userId;

    private String title;

    private String description;

    private OffsetDateTime startTime;

    private OffsetDateTime endTime;

    private String location;

    private Boolean isAiExtracted;

    private Long sourceMessageId;

    private OffsetDateTime createdAt;
}
