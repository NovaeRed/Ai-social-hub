package cn.redture.aiEngine.llm.config;

import cn.redture.aiEngine.llm.util.ModelProviderUtil;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一模型目录，集中检索候选模型与厂商配置。
 */
@Component
@RequiredArgsConstructor
public class ModelCatalog {

    private final AiChatProperties chatProperties;
    private final AiProviderProperties providerProperties;

    private String getDefaultModelCode() {
        return ModelProviderUtil.trimToNull(chatProperties.getDefaultModel());
    }

    /**
     * 获取默认模型规格。
     */
    public ModelSpec getDefaultModelSpec() {
        String defaultModelCode = getDefaultModelCode();
        if (defaultModelCode == null || defaultModelCode.isBlank()) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "缺少 ai.chat.default-model 配置", ErrorCodes.MODEL_OPTION_INVALID);
        }
        return resolveModelCodeStrict(defaultModelCode);
    }

    /**
     * 根据模型编码解析 provider/model。
     * <p>
     * 支持候选 ID 或模型名两种输入形式。
     * </p>
     */
    public ModelSpec resolveModelCode(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return null;
        }
        return resolveCandidateId(modelCode.trim(), false);
    }

    private ModelSpec resolveModelCodeStrict(String modelCode) {
        return resolveCandidateId(modelCode, true);
    }

    /**
     * 判断候选模型列表是否已配置。
     */
    private boolean hasCandidates() {
        List<AiChatProperties.ModelCandidateConfig> candidates = chatProperties.getCandidates();
        return candidates != null && !candidates.isEmpty();
    }

    /**
     * 按候选标识解析模型规格。
     *
     * @param candidateId 候选 ID 或模型名
     * @param strict 是否严格模式（严格模式下解析失败直接抛异常）
     * @return 模型规格
     */
    private ModelSpec resolveCandidateId(String candidateId, boolean strict) {
        if (candidateId == null || candidateId.isBlank()) {
            return null;
        }

        String normalizedId = candidateId.trim();
        if (!hasCandidates()) {
            if (strict) {
                throw new BaseException(HttpStatus.BAD_REQUEST, "ai.chat.candidates 未配置任何模型", ErrorCodes.MODEL_OPTION_INVALID);
            }
            return null;
        }

        AiChatProperties.ModelCandidateConfig candidate = findEnabledCandidateByIdOrModel(normalizedId);
        if (candidate != null) {
            String provider = ModelProviderUtil.normalizeProvider(candidate.getProvider());
            String model = ModelProviderUtil.trimToNull(candidate.getModel());
            if (provider.isBlank() || model == null || model.isBlank()) {
                if (strict) {
                    throw new BaseException(HttpStatus.BAD_REQUEST, "模型候选 ID=" + normalizedId + " 的 provider 或 model 配置不完整", ErrorCodes.MODEL_OPTION_INVALID);
                }
                return null;
            }
            return new ModelSpec(normalizedId, provider, model);
        }

        if (strict) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型候选及模型名称=" + normalizedId + " 不存在", ErrorCodes.MODEL_OPTION_INVALID);
        }

        return null;
    }

    /**
     * 在启用候选中按 ID 或模型名查找配置。
     *
     * @param candidateIdOrModel 候选 ID 或模型名
     * @return 候选配置
     */
    public AiChatProperties.ModelCandidateConfig findEnabledCandidateByIdOrModel(String candidateIdOrModel) {
        String normalizedId = ModelProviderUtil.trimToNull(candidateIdOrModel);
        if (normalizedId == null) {
            return null;
        }

        List<AiChatProperties.ModelCandidateConfig> candidates = chatProperties.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        for (AiChatProperties.ModelCandidateConfig candidate : candidates) {
            if (candidate == null || !Boolean.TRUE.equals(candidate.getEnabled())) {
                continue;
            }

            String candidateId = ModelProviderUtil.trimToNull(candidate.getId());
            String candidateModel = ModelProviderUtil.normalizeModelName(candidate.getModel());
            if (normalizedId.equals(candidateId) || normalizedId.equals(candidateModel)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 在启用候选中按 provider + model 查找配置。
     *
     * @param provider 厂商编码
     * @param modelName 模型名
     * @return 候选配置
     */
    public AiChatProperties.ModelCandidateConfig findEnabledCandidateByProviderAndModel(String provider, String modelName) {
        String normalizedProvider = ModelProviderUtil.normalizeProvider(provider);
        String normalizedModelName = ModelProviderUtil.normalizeModelName(modelName);
        if (normalizedProvider.isBlank() || normalizedModelName.isBlank()) {
            return null;
        }

        List<AiChatProperties.ModelCandidateConfig> candidates = chatProperties.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        for (AiChatProperties.ModelCandidateConfig candidate : candidates) {
            if (candidate == null || !Boolean.TRUE.equals(candidate.getEnabled())) {
                continue;
            }

            if (normalizedProvider.equals(ModelProviderUtil.normalizeProvider(candidate.getProvider()))
                    && normalizedModelName.equals(ModelProviderUtil.normalizeModelName(candidate.getModel()))) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * 查找启用状态的厂商配置。
     *
     * @param provider 厂商编码
     * @return 厂商配置
     */
    public AiProviderProperties.ProviderConfig findEnabledProviderConfig(String provider) {
        String normalizedProvider = ModelProviderUtil.normalizeProvider(provider);
        if (normalizedProvider.isBlank()) {
            return null;
        }

        if (providerProperties.getProviders() == null || providerProperties.getProviders().isEmpty()) {
            return null;
        }

        AiProviderProperties.ProviderConfig config = providerProperties.getProviders().get(normalizedProvider);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            return config;
        }

        for (var entry : providerProperties.getProviders().entrySet()) {
            if (normalizedProvider.equals(ModelProviderUtil.normalizeProvider(entry.getKey()))) {
                if (entry.getValue() != null && Boolean.TRUE.equals(entry.getValue().getEnabled())) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * 配置中心解析出的模型规格。
     *
     * @param modelCode 请求模型编码（候选 ID 或模型名）
     * @param provider 厂商编码
     * @param modelName 模型名
     */
    public record ModelSpec(String modelCode, String provider, String modelName) {
    }
}