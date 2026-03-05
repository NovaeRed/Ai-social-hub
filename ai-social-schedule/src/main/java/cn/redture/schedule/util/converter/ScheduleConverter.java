package cn.redture.schedule.util.converter;

import cn.redture.schedule.pojo.dto.ScheduleDTO;
import cn.redture.schedule.pojo.entity.Schedule;
import cn.redture.schedule.pojo.vo.ScheduleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ScheduleConverter {
    ScheduleConverter INSTANCE = Mappers.getMapper(ScheduleConverter.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Schedule toEntity(ScheduleDTO dto);

    ScheduleVO toVO(Schedule entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDto(ScheduleDTO dto, @MappingTarget Schedule entity);
}
