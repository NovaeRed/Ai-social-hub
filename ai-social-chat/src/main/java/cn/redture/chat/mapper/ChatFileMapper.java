package cn.redture.chat.mapper;

import cn.redture.chat.pojo.entity.ChatFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatFileMapper extends BaseMapper<ChatFile> {

    @Select("SELECT * FROM chat_files WHERE public_id = #{publicId} AND deleted_at IS NULL")
    ChatFile selectActiveByPublicId(@Param("publicId") String publicId);
}
