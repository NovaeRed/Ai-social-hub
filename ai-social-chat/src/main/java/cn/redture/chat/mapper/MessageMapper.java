package cn.redture.chat.mapper;

import cn.redture.chat.pojo.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

	List<Message> selectByCursor(@Param("conversationId") Long conversationId,
								 @Param("cursorId") Long cursorId,
								 @Param("pageSize") int pageSize);
}
