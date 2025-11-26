package cn.redture.aiEngine.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAiContextMapper {

    @Delete("""
        DELETE FROM user_ai_vectors
        WHERE user_id = #{userId}
        """)
    int deleteVectorsByUserId(@Param("userId") Long userId);

    @Delete("""
        DELETE FROM user_ai_contexts
        WHERE user_id = #{userId}
        """)
    int deleteContextsByUserId(@Param("userId") Long userId);
}
