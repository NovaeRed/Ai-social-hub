package cn.redture.schedule.service;

import cn.redture.schedule.pojo.dto.ScheduleDTO;
import cn.redture.schedule.pojo.vo.ScheduleVO;
import java.time.OffsetDateTime;
import java.util.List;

public interface ScheduleService {
    List<ScheduleVO> listSchedules(Long userId, OffsetDateTime startTime, OffsetDateTime endTime);
    ScheduleVO createSchedule(Long userId, ScheduleDTO dto);
    ScheduleVO updateSchedule(Long userId, String publicId, ScheduleDTO dto);
    void deleteSchedule(Long userId, String publicId);
}
