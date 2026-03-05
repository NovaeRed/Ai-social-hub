package cn.redture.schedule.mapper;

import cn.redture.schedule.pojo.entity.Schedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日程 Mapper
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {
}
