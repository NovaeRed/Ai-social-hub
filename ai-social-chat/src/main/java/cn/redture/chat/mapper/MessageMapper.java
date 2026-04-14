package cn.redture.chat.mapper;

import cn.redture.chat.pojo.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

	/**
	 * 按游标获取会话消息
	 */
	List<Message> selectByCursor(@Param("conversationId") Long conversationId,
								 @Param("cursorId") Long cursorId,
								 @Param("minId") Long minId,
								 @Param("limit") int limit);
	/**
	 * 获取会话的最近N条消息（用于AI上下文）
	 */
	@Select("SELECT * FROM messages " +
			"WHERE conversation_id = #{conversationId} AND deleted_at IS NULL " +
			"ORDER BY created_at DESC " +
			"LIMIT #{limit}")
	List<Message> selectRecentMessages(@Param("conversationId") Long conversationId,
									   @Param("limit") int limit);

	/**
	 * 按ID列表查询消息（用于Persona Analysis显式选择）
	 */
	@Select("SELECT * FROM messages " +
			"WHERE id IN (${ids}) AND deleted_at IS NULL " +
			"ORDER BY created_at ASC")
	List<Message> selectByIds(@Param("ids") String ids);

	/**
	 * 获取用户在指定会话中的消息
	 */
	@Select("SELECT * FROM messages " +
			"WHERE conversation_id = #{conversationId} AND sender_id = #{userId} " +
			"AND deleted_at IS NULL " +
			"ORDER BY created_at DESC " +
			"LIMIT #{limit}")
	List<Message> selectByConversationAndUser(@Param("conversationId") Long conversationId,
											  @Param("userId") Long userId,
											  @Param("limit") int limit);

	/**
	 * 获取用户的最近N条消息（跨会话，用于Persona Analysis）
	 */
	@Select("SELECT * FROM messages " +
			"WHERE sender_id = #{userId} AND deleted_at IS NULL " +
			"ORDER BY created_at DESC " +
			"LIMIT #{limit}")
	List<Message> selectByUserId(@Param("userId") Long userId,
								 @Param("limit") int limit);
}
