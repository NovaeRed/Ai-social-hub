package cn.redture.chat.mapper;

import cn.redture.chat.pojo.entity.ConversationMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {
    int insertBatch(List<ConversationMember> members);

    @Update("UPDATE conversation_members SET last_read_message_id = #{latestMessageId}, last_read_at = NOW() WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    void updateLastReadMessageId(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("latestMessageId") Long latestMessageId);

    @Select("SELECT * FROM conversation_members WHERE conversation_id = #{conversationId} AND user_id = #{userId}")
    ConversationMember selectByConversationIdAndUserId(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    int batchUpdateRoleToAdmin(@Param("conversationId") Long conversationId, @Param("userIds") List<Long> userIds);

    void batchUpdateRoleToMember(@Param("conversationId") Long conversationId, @Param("userIds") List<Long> userIds);

    void updateRoleToOwner(@Param("conversationId") Long conversationId,@Param("userId") Long userId);
}
