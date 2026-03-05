package cn.redture.schedule.service.impl;

import cn.redture.common.exception.businessException.ResourceNotFoundException;
import cn.redture.common.util.IdUtil;
import cn.redture.schedule.mapper.ScheduleMapper;
import cn.redture.schedule.pojo.dto.ScheduleDTO;
import cn.redture.schedule.pojo.entity.Schedule;
import cn.redture.schedule.pojo.vo.ScheduleVO;
import cn.redture.schedule.service.ScheduleService;
import cn.redture.schedule.util.converter.ScheduleConverter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Resource
    private ScheduleMapper scheduleMapper;

    @Override
    public List<ScheduleVO> listSchedules(Long userId, OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, userId)
                .ge(Schedule::getStartTime, startTime)
                .le(Schedule::getEndTime, endTime)
                .orderByAsc(Schedule::getStartTime));

        return schedules.stream()
                .map(ScheduleConverter.INSTANCE::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public ScheduleVO createSchedule(Long userId, ScheduleDTO dto) {
        Schedule schedule = ScheduleConverter.INSTANCE.toEntity(dto);
        schedule.setUserId(userId);
        schedule.setPublicId(IdUtil.nextId());
        schedule.setCreatedAt(OffsetDateTime.now());

        scheduleMapper.insert(schedule);
        log.info("Created schedule {} for user {}", schedule.getPublicId(), userId);
        return ScheduleConverter.INSTANCE.toVO(schedule);
    }

    @Override
    public ScheduleVO updateSchedule(Long userId, String publicId, ScheduleDTO dto) {
        Schedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, userId)
                .eq(Schedule::getPublicId, publicId));

        if (schedule == null) {
            throw new ResourceNotFoundException("日程不存在");
        }

        ScheduleConverter.INSTANCE.updateEntityFromDto(dto, schedule);
        scheduleMapper.updateById(schedule);
        log.info("Updated schedule {} for user {}", publicId, userId);
        return ScheduleConverter.INSTANCE.toVO(schedule);
    }

    @Override
    public void deleteSchedule(Long userId, String publicId) {
        int rows = scheduleMapper.delete(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, userId)
                .eq(Schedule::getPublicId, publicId));

        if (rows == 0) {
            throw new ResourceNotFoundException("日程不存在");
        }
        log.info("Deleted schedule {} for user {}", publicId, userId);
    }
}
