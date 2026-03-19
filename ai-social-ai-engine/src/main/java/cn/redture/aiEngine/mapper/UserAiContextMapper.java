package cn.redture.aiEngine.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAiContextMapper {

    @Delete("DELETE FROM user_ai_profiles WHERE user_id = #{userId}")
    int deleteProfilesByUserId(@Param("userId") Long userId);
}
