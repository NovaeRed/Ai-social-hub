package cn.redture.aiEngine.service;

import cn.redture.aiEngine.pojo.dto.PolishRequest;
import cn.redture.aiEngine.pojo.dto.ScheduleRequest;
import cn.redture.aiEngine.pojo.dto.SmartReplyRequest;
import cn.redture.aiEngine.pojo.dto.SummarizeRequest;
import cn.redture.aiEngine.pojo.dto.TranslationRequest;
import cn.redture.aiEngine.pojo.vo.ScheduleExtractionVO;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import reactor.core.publisher.Flux;

/**
 * AI 在线交互服务接口。
 * 提供面向前端实时调用的流式与同步交互能力。
 */
public interface AiOnlineInteractionService {

    Flux<StreamOutputVO> polishStream(Long userId, PolishRequest request);

    Flux<StreamOutputVO> translateStream(Long userId, TranslationRequest request);

    Flux<StreamOutputVO> smartReplyStream(Long userId, SmartReplyRequest request);

    Flux<StreamOutputVO> summarizeStream(Long userId, SummarizeRequest request);

    ScheduleExtractionVO extractSchedule(Long userId, ScheduleRequest request);
}
