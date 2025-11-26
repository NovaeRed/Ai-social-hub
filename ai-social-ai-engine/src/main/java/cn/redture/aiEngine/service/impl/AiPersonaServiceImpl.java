package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.dto.AiPersonaTaskDTO;
import cn.redture.aiEngine.mapper.UserAiContextMapper;
import cn.redture.aiEngine.model.AiPersonaTaskType;
import cn.redture.aiEngine.service.AiPersonaService;
import cn.redture.common.util.JsonUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.redture.common.constants.RedisConstants.PERSONA_TASK_QUEUE_KEY;

@Slf4j
@Service
public class AiPersonaServiceImpl implements AiPersonaService {

    @Resource
    private UserAiContextMapper userAiContextMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onAiAnalysisToggled(Long userId, boolean enabled) {
        AiPersonaTaskDTO task = new AiPersonaTaskDTO();
        task.setUserId(userId);
        task.setType(enabled ? AiPersonaTaskType.AI_PERSONA_INIT : AiPersonaTaskType.AI_PERSONA_AUTH_DISABLED);

        String taskJson = JsonUtil.toJson(task);
        stringRedisTemplate.opsForList().leftPush(PERSONA_TASK_QUEUE_KEY, taskJson);
        log.debug("投递 AI 画像任务到队列 {}: {}", PERSONA_TASK_QUEUE_KEY, taskJson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearPersonaByUserId(Long userId) {
        int vectors = userAiContextMapper.deleteVectorsByUserId(userId);
        int contexts = userAiContextMapper.deleteContextsByUserId(userId);

        String personaCacheKey = "ai:persona:" + userId;
        stringRedisTemplate.delete(personaCacheKey);

        log.debug("清除用户 {} 的 AI 画像数据完成，contexts={}, vectors={}", userId, contexts, vectors);
    }
}
