package cn.redture.aiEngine.service;

import cn.redture.aiEngine.pojo.dto.*;
import cn.redture.aiEngine.pojo.vo.PersonaAnalysisVO;
import cn.redture.aiEngine.pojo.vo.ScheduleExtractionVO;
import cn.redture.aiEngine.pojo.vo.StreamOutputVO;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI交互服务接口
 * 整合了文本润色、翻译、智能回复、日程提取、内容总结、性格分析等AI交互功能
 */
public interface AiInteractionService {

    /**
     * 流式文本润色
     */
    Flux<StreamOutputVO> polishStream(Long userId, PolishRequest request);

    /**
     * 流式翻译
     */
    Flux<StreamOutputVO> translateStream(Long userId, TranslationRequest request);

    /**
     * 流式智能回复
     */
    Flux<StreamOutputVO> smartReplyStream(Long userId, SmartReplyRequest request);

    /**
     * 流式内容总结
     */
    Flux<StreamOutputVO> summarizeStream(Long userId, SummarizeRequest request);

    /**
     * 日程提取 (同步)
     */
    ScheduleExtractionVO extractSchedule(Long userId, ScheduleRequest request);

    /**
     * 性格分析 (异步)
     */
    PersonaAnalysisVO analyzePersonaAsync(Long userId, PersonaAnalysisRequest request);

    /**
     * 初始化用户画像 (基于历史消息)
     */
    void initPersona(Long userId);

    /**
     * 禁用用户画像 (清理或标记)
     */
    void disablePersona(Long userId);
}
