package cn.redture.identity.util.converter;

import cn.redture.identity.pojo.entity.Friendship;
import cn.redture.identity.pojo.entity.User;
import cn.redture.identity.pojo.vo.FriendSummaryVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface FriendshipConverter {

    FriendshipConverter INSTANCE = Mappers.getMapper(FriendshipConverter.class);

     

}
