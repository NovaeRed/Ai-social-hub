package cn.redture.aiEngine.llm.config;

import cn.redture.aiEngine.llm.util.ModelProviderUtil;

import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型配置中心服务
 */
@Component
public class ModelRoutingConfigCenter {

    private final AiChatProperties chatProperties;

    public ModelRoutingConfigCenter(AiChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    /**
     * 获取默认模型规格
     */
    public ModelSpec getDefaultModelSpec() {
        if (chatProperties.getDefaultModel() == null || chatProperties.getDefaultModel().isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST,
                    "缺少 ai.chat.default-model 配置",
                    ErrorCodes.MODEL_OPTION_INVALID);
        }
        return resolveCandidateId(chatProperties.getDefaultModel(), true);
    }

    /**
     * 根据模型编码解析 provider/model
     */
    public ModelSpec resolveModelCode(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return null;
        }

        String normalizedCode = modelCode.trim();
        return resolveCandidateId(normalizedCode, false);
    }

    /**
     * 从配置结构 (chat.candidates) 中根据 candidate ID 解析模型规格
     */
    private ModelSpec resolveCandidateId(String candidateId, boolean strict) {
        if (candidateId == null || candidateId.isBlank()) {
            return null;
        }

        String normalizedId = candidateId.trim();
        List<AiChatProperties.ModelCandidateConfig> candidates = chatProperties.getCandidates();

        if (candidates == null || candidates.isEmpty()) {
            if (strict) {
                throw new BaseException(HttpStatus.BAD_REQUEST,
                        "ai.chat.candidates 未配置任何模型",
                        ErrorCodes.MODEL_OPTION_INVALID);
            }
            return null;
        }

        for (AiChatProperties.ModelCandidateConfig candidate : candidates) {
            if (candidate == null || !Boolean.TRUE.equals(candidate.getEnabled())) {
                continue;
            }
            if (normalizedId.equals(candidate.getId()) || normalizedId.equals(candidate.getModel())) {
                String provider = ModelProviderUtil.normalizeProvider(candidate.getProvider());
                String model = ModelProviderUtil.trimToNull(candidate.getModel());
                if (provider.isBlank() || model == null || model.isBlank()) {
                    if (strict) {
                        throw new BaseException(HttpStatus.BAD_REQUEST,
                                "模型候选 ID=" + normalizedId + " 的 provider 或 model 配置不完整",
                                ErrorCodes.MODEL_OPTION_INVALID);
                    }
                    return null;
                }
                return new ModelSpec(normalizedId, provider, model);
            }
        }

        if (strict) {
            throw new BaseException(HttpStatus.BAD_REQUEST,
                    "模型候选及模型名称=" + normalizedId + " 不存在",
                    ErrorCodes.MODEL_OPTION_INVALID);
        }
        return null;
    }

    /**
     * 配置中心解析出的模型规格
     */
    public record ModelSpec(String modelCode, String provider, String modelName) {
    }
}
