package cn.redture.chat.util.converter;

import cn.redture.chat.pojo.entity.Message;
import cn.redture.chat.pojo.vo.ConversationSummaryVO;
import cn.redture.chat.pojo.vo.MessageItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface MessageConverter {

    MessageConverter INSTANCE = Mappers.getMapper(MessageConverter.class);

    ConversationSummaryVO.LatestMessageVO toLatestMessageVO(Message message);

    MessageItemVO toMessageItemVO(Message message);
}
