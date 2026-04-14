package cn.redture.chat.mapper;

import cn.redture.chat.pojo.dto.ConversationTimelineDTO;
import cn.redture.chat.pojo.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    List<ConversationTimelineDTO> selectTimelineConversations(@Param("userId") Long userId,
                                                              @Param("cursorId") Long cursorId,
                                                              @Param("pageSize") int pageSize);

    Conversation selectPrivateConversationBetween(@Param("userId1") Long userId1,
                                                  @Param("userId2") Long userId2);


    List<Conversation> selectUserGroupsByCursor(@Param("userId") Long userId,
                                                @Param("cursor") Long cursor,
                                                @Param("limit") int limit,
                                                @Param("keyword") String keyword);

    @Update("UPDATE conversations SET member_count = GREATEST(0, member_count + #{delta}) WHERE id = #{conversationId}")
    int incrementMemberCount(@Param("conversationId") Long conversationId, @Param("delta") int delta);

    @Update("UPDATE conversations SET member_count = GREATEST(0, member_count - #{delta}) WHERE id = #{conversationId}")
    int decrementMemberCount(@Param("conversationId") Long conversationId, @Param("delta") int delta);

}
