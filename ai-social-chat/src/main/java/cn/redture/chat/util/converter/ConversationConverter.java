package cn.redture.chat.util.converter;

import cn.redture.chat.pojo.entity.Conversation;
import cn.redture.chat.pojo.vo.ConversationCreatedResultVO;
import cn.redture.chat.pojo.vo.ConversationSummaryVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ConversationConverter {

    ConversationConverter INSTANCE = Mappers.getMapper(ConversationConverter.class);

    ConversationSummaryVO toConversationSummaryVO(Conversation conversation);

    ConversationCreatedResultVO toConversationCreatedResultVO(Conversation conversation);
}
