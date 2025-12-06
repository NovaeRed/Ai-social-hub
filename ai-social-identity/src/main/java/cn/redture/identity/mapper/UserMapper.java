package cn.redture.identity.mapper;

import cn.redture.identity.pojo.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

	User selectByPublicId(@Param("publicId") String publicId);

    List<Long> selectIdsByPublicIds(List<String> publicIds);
}

