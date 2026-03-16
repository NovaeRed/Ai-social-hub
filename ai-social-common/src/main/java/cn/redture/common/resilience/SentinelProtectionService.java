package cn.redture.common.resilience;

import cn.redture.common.exception.businessException.CircuitBreakerException;
import cn.redture.common.exception.businessException.RateLimitException;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Sentinel 保护执行器，负责统一执行资源进入、退出和异常语义映射。
 */
@Slf4j
@Service
public class SentinelProtectionService {

    public <T> T call(String resource, Supplier<T> supplier) {
        Entry entry = null;
        try {
            entry = SphU.entry(resource);
            return supplier.get();
        } catch (BlockException ex) {
            throw mapBlockException(resource, ex);
        } catch (Exception ex) {
            Tracer.trace(ex);
            throw ex;
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private RuntimeException mapBlockException(String resource, BlockException ex) {
        if (ex instanceof DegradeException) {
            log.warn("Sentinel degrade triggered, resource={}, type={}", resource, ex.getClass().getSimpleName());
            return new CircuitBreakerException("服务暂时不可用，请稍后重试");
        }
        log.warn("Sentinel flow control triggered, resource={}, type={}", resource, ex.getClass().getSimpleName());
        return new RateLimitException("请求过于频繁，请稍后重试");
    }
}
