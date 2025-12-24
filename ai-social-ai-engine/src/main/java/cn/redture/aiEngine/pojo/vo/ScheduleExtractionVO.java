package cn.redture.aiEngine.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 日程提取响应VO
 */
@Data
public class ScheduleExtractionVO {
    
    /**
     * 提取出的日程列表
     */
    private List<ScheduleItemVO> schedules;

    @Data
    public static class ScheduleItemVO {
        /**
         * 日程标题
         */
        private String title;
        
        /**
         * 时间描述
         */
        private String time;
        
        /**
         * 地点（可选）
         */
        private String location;
        
        /**
         * 参与者（可选）
         */
        private List<String> participants;
    }
}
