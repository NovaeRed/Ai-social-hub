package cn.redture.aiEngine.controller;

import cn.redture.aiEngine.pojo.vo.AiModelVO;
import cn.redture.aiEngine.pojo.vo.AiProfileVO;
import cn.redture.aiEngine.pojo.vo.AiUsageVO;
import cn.redture.aiEngine.service.AiConfigService;
import cn.redture.common.pojo.model.RestResult;
import cn.redture.common.util.SecurityContextHolderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI配置与信息控制器 初版设计
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;

    /**
     * 获取可用AI模型列表
     */
    @GetMapping("/models")
    public RestResult<Map<String, List<AiModelVO>>> getModels() {
        return RestResult.success(Map.of("models", aiConfigService.getAvailableModels()));
    }

    /**
     * 获取用户AI画像
     */
    @GetMapping("/profiles")
    public RestResult<Map<String, List<AiProfileVO>>> getProfiles(@RequestParam(value = "profile_type", required = false) String profileType) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.success(Map.of("items", aiConfigService.getUserProfiles(userId, profileType)));
    }

    /**
     * 获取AI使用统计
     */
    @GetMapping("/usage")
    public RestResult<AiUsageVO> getUsage(@RequestParam(value = "date_from", required = false) String dateFrom,
                                          @RequestParam(value = "date_to", required = false) String dateTo,
                                          @RequestParam(value = "provider", required = false) String provider) {
        Long userId = SecurityContextHolderUtil.getUserId();
        return RestResult.success(aiConfigService.getUsageStats(userId, dateFrom, dateTo, provider));
    }
}
