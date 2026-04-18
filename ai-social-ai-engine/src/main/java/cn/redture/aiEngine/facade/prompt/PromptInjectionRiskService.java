package cn.redture.aiEngine.facade.prompt;

import cn.redture.aiEngine.pojo.enums.AiTaskType;
import cn.redture.common.constants.ErrorCodes;
import cn.redture.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户输入 Prompt 注入风控服务。
 */
@Slf4j
@Component
public class PromptInjectionRiskService {

    private static final Set<String> RISKY_PATTERNS = Set.of(
            "忽略", "ignore", "forget", "你现在", "you are now",
            "重复", "repeat", "显示提示", "show prompt", "system prompt"
    );

    private static final Pattern ROLE_SWITCH_PATTERN = Pattern.compile(
            ".*(现在|now)\\s*(扮演|是|act as|are).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern PROMPT_EXTRACTION_PATTERN = Pattern.compile(
            ".*(显示|show|输出|output|打印|print).*(提示|prompt|指令|instruction|系统|system).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final int HIGH_RISK_THRESHOLD = 30;

    /**
     * 对输入进行注入风控校验。
     *
     * @param taskType 任务类型
     * @param inputTexts 需评估的输入文本
     */
    public void validate(AiTaskType taskType, Map<String, String> inputTexts) {
        if (inputTexts == null || inputTexts.isEmpty()) {
            return;
        }

        int totalRisk = 0;
        int maxRisk = 0;
        String maxRiskField = "";

        for (Map.Entry<String, String> entry : inputTexts.entrySet()) {
            int score = calculateRiskScore(entry.getValue());
            totalRisk += score;
            if (score > maxRisk) {
                maxRisk = score;
                maxRiskField = entry.getKey();
            }
        }

        if (maxRisk > HIGH_RISK_THRESHOLD || totalRisk >= HIGH_RISK_THRESHOLD + 10) {
            log.warn("Prompt 注入风险触发拦截: taskType={}, field={}, maxRisk={}, totalRisk={}",
                    taskType, maxRiskField, maxRisk, totalRisk);
            throw new BaseException(HttpStatus.BAD_REQUEST,
                    "输入内容触发安全策略，请调整后重试",
                    ErrorCodes.PROMPT_INJECTION_BLOCKED);
        }

        if (totalRisk > 0) {
            log.info("Prompt 风险检测命中: taskType={}, totalRisk={}", taskType, totalRisk);
        }
    }

    /**
     * 计算输入风险分。
     *
     * @param userInput 用户输入
     * @return 风险分数
     */
    public int calculateRiskScore(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return 0;
        }

        String lowerInput = userInput.toLowerCase(Locale.ROOT);
        int riskScore = 0;

        for (String pattern : RISKY_PATTERNS) {
            if (lowerInput.contains(pattern.toLowerCase(Locale.ROOT))) {
                riskScore += 10;
            }
        }

        if (ROLE_SWITCH_PATTERN.matcher(lowerInput).find()) {
            riskScore += 20;
        }

        if (PROMPT_EXTRACTION_PATTERN.matcher(lowerInput).find()) {
            riskScore += 25;
        }

        return riskScore;
    }

    /**
     * 判断是否高风险。
     *
     * @param userInput 用户输入
     * @return 是否高风险
     */
    public boolean isHighRisk(String userInput) {
        return calculateRiskScore(userInput) > HIGH_RISK_THRESHOLD;
    }
}
