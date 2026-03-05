package cn.redture.schedule.controller;

import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import cn.redture.schedule.pojo.dto.ScheduleDTO;
import cn.redture.schedule.pojo.vo.ScheduleVO;
import cn.redture.schedule.service.ScheduleService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/schedules")
public class ScheduleController {

    @Resource
    private ScheduleService scheduleService;

    @GetMapping
    public RestResult<List<ScheduleVO>> listSchedules(
            @RequestParam("start_time") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam("end_time") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.success(scheduleService.listSchedules(userId, startTime, endTime));
    }

    @PostMapping
    public RestResult<ScheduleVO> createSchedule(@RequestBody ScheduleDTO dto) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.created(scheduleService.createSchedule(userId, dto));
    }

    @PutMapping("/{public_id}")
    public RestResult<ScheduleVO> updateSchedule(
            @PathVariable("public_id") String publicId,
            @RequestBody ScheduleDTO dto) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.success(scheduleService.updateSchedule(userId, publicId, dto));
    }

    @DeleteMapping("/{public_id}")
    public RestResult<Void> deleteSchedule(@PathVariable("public_id") String publicId) {
        Long userId = SecurityContextHolderUtil.getUserId();
        scheduleService.deleteSchedule(userId, publicId);
        return RestResult.noContent();
    }
}
