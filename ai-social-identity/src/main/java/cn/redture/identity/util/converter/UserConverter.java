package cn.redture.identity.util.converter;

import cn.redture.identity.pojo.dto.UpdateUserDTO;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.vo.UserInformation;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserConverter {

    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    UserInformation toUserInformation(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromDto(UpdateUserDTO dto, @MappingTarget User user);
}

