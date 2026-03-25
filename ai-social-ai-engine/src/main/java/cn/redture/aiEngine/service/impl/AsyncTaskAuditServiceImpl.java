package cn.redture.aiEngine.service.impl;

import cn.redture.aiEngine.pojo.enums.AsyncTaskDomain;
import cn.redture.aiEngine.service.AsyncTaskAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 异步任务审计与指标服务实现。
 */
@Slf4j
@Service
public class AsyncTaskAuditServiceImpl implements AsyncTaskAuditService {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    @Override
    public void record(String action, AsyncTaskDomain domain, Map<String, Object> metadata) {
        String key = action + "|" + (domain == null ? "UNKNOWN" : domain.name());
        counters.computeIfAbsent(key, k -> new LongAdder()).increment();
        long current = counters.get(key).sum();
        log.info("[ASYNC_AUDIT] action={}, domain={}, count={}, metadata={}", action, domain, current, metadata);
    }
}
