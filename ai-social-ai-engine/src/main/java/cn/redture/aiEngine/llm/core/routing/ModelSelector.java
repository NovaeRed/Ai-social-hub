package cn.redture.aiEngine.llm.core.routing;

import cn.redture.aiEngine.llm.config.ModelCatalog;
import cn.redture.aiEngine.llm.util.ModelProviderUtil;
import cn.redture.aiEngine.mapper.AiModelCapabilityMapper;
import cn.redture.aiEngine.pojo.entity.AiModelCapability;
import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 模型路由选择器
 *
 * <p>负责将请求参数、配置中心与能力开关统一解析为可执行模型</p>
 */
@Component
@RequiredArgsConstructor
public class ModelSelector {

    private final ModelCatalog modelCatalog;
    private final AiModelCapabilityMapper aiModelCapabilityMapper;

    /**
     * 在线任务路由：请求参数 > 默认配置。
        *
        * @param taskType 任务类型
        * @param params 请求参数
        * @return 路由决策结果
     */
    public ModelRouteDecision resolveModelRoute(AiTaskType taskType, Map<String, Object> params) {
        String explicitOptionCode = readOptionCode(params);
        String requestedCode;
        String provider;
        String modelName;

        if (explicitOptionCode != null) {
            ParsedOptionCode parsed = parseOptionCode(explicitOptionCode, true);
            requestedCode = explicitOptionCode;
            provider = parsed.provider();
            modelName = parsed.modelName();
        } else {
            ModelCatalog.ModelSpec defaultSpec = modelCatalog.getDefaultModelSpec();
            requestedCode = defaultSpec.modelCode();
            provider = defaultSpec.provider();
            modelName = defaultSpec.modelName();
        }

        AiModelCapability capability = findEnabledCapability(provider, modelName, taskType);
        if (capability == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "所选模型未启用或不可用", ErrorCodes.MODEL_NOT_ENABLED);
        }

        return new ModelRouteDecision(requestedCode,
                ModelProviderUtil.normalizeProvider(capability.getProvider()),
                capability.getModelName());
    }

    /**
     * 异步任务固定默认路由。
        *
        * @param taskType 任务类型
        * @return 默认路由决策结果
     */
    public ModelRouteDecision resolveSystemDefaultRoute(AiTaskType taskType) {
        ModelCatalog.ModelSpec defaultSpec = modelCatalog.getDefaultModelSpec();
        String requestedCode = defaultSpec.modelCode();

        AiModelCapability capability = findEnabledCapability(defaultSpec.provider(), defaultSpec.modelName(), taskType);
        if (capability == null) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "系统默认模型未启用、不可用或不支持当前任务类型", ErrorCodes.MODEL_NOT_ENABLED);
        }

        return new ModelRouteDecision(requestedCode,
                ModelProviderUtil.normalizeProvider(capability.getProvider()),
                capability.getModelName());
    }

    /**
     * 从输入参数中读取模型选项编码。
     *
     * @param params 请求参数
     * @return 模型选项编码
     */
    private String readOptionCode(Map<String, Object> params) {
        if (params == null) {
            return null;
        }

        Object optionObj = params.get("model_option_code");
        if (!(optionObj instanceof String optionCode) || optionCode.isBlank()) {
            return null;
        }
        return optionCode.trim();
    }

    /**
     * 解析 model_option_code。
     *
     * @param optionCode 模型选项编码
     * @param strict 是否严格模式
     * @return 解析后的 provider/model
     */
    private ParsedOptionCode parseOptionCode(String optionCode, boolean strict) {
        String normalized = optionCode == null ? "" : optionCode.trim();

        if (normalized.isBlank()) {
            if (strict) {
                throw new BaseException(HttpStatus.BAD_REQUEST, "model_option_code 不存在或未配置", ErrorCodes.MODEL_OPTION_INVALID);
            }
            return new ParsedOptionCode("", "");
        }

        if (!normalized.contains(":")) {
            ModelCatalog.ModelSpec modelSpec = modelCatalog.resolveModelCode(normalized);
            if (modelSpec != null) {
                return new ParsedOptionCode(ModelProviderUtil.normalizeProvider(modelSpec.provider()), modelSpec.modelName());
            }

            if (strict) {
                throw new BaseException(HttpStatus.BAD_REQUEST, "model_option_code 不存在或未配置", ErrorCodes.MODEL_OPTION_INVALID);
            }
        }

        int index = normalized.indexOf(':');
        if (index <= 0 || index >= normalized.length() - 1) {
            if (strict) {
                throw new BaseException(HttpStatus.BAD_REQUEST, "model_option_code 格式非法，必须为 provider:model_name", ErrorCodes.MODEL_OPTION_INVALID);
            }
            return new ParsedOptionCode("", "");
        }

        String provider = ModelProviderUtil.normalizeProvider(normalized.substring(0, index));
        String modelName = ModelProviderUtil.normalizeModelName(normalized.substring(index + 1));
        if (provider.isBlank() || modelName.isBlank()) {
            if (strict) {
                throw new BaseException(HttpStatus.BAD_REQUEST, "model_option_code 格式非法，provider 或 model_name 为空", ErrorCodes.MODEL_OPTION_INVALID);
            }
        }
        return new ParsedOptionCode(provider, modelName);
    }

    /**
     * 按任务类型校验模型能力是否可用。
     *
     * @param provider 厂商编码
     * @param modelName 模型名
     * @param taskType 任务类型
     * @return 匹配到的能力记录
     */
    private AiModelCapability findEnabledCapability(String provider, String modelName, AiTaskType taskType) {
        String normalizedProvider = ModelProviderUtil.normalizeProvider(provider);
        AiModelCapability capability = aiModelCapabilityMapper.selectOne(
                new LambdaQueryWrapper<AiModelCapability>()
                        .eq(AiModelCapability::getProvider, normalizedProvider)
                        .eq(AiModelCapability::getModelName, modelName)
                        .eq(AiModelCapability::getCapabilityType, taskType)
                        .eq(AiModelCapability::getIsEnabled, true)
                        .last("LIMIT 1")
        );

        if (capability != null) {
            return capability;
        }

        boolean modelEnabled = aiModelCapabilityMapper.selectCount(
                new LambdaQueryWrapper<AiModelCapability>()
                        .eq(AiModelCapability::getProvider, normalizedProvider)
                        .eq(AiModelCapability::getModelName, modelName)
                        .eq(AiModelCapability::getIsEnabled, true)
        ) > 0;

        if (modelEnabled) {
            throw new BaseException(HttpStatus.BAD_REQUEST, "模型不支持当前任务类型", ErrorCodes.MODEL_CAPABILITY_MISMATCH);
        }

        return null;
    }

    /**
     * 解析后的模型选项结构。
     *
     * @param provider 厂商编码
     * @param modelName 模型名
     */
    private record ParsedOptionCode(String provider, String modelName) {
    }
}
