package cn.redture.chat.controller;

import cn.redture.chat.sse.SseEmitterService;
import cn.redture.common.util.SecurityContextHolderUtil;

import jakarta.annotation.Resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE 订阅入口。 */
@RestController
@RequestMapping("/sse")
public class SseController {

    @Resource private SseEmitterService sseEmitterService;

    /**
     * 为当前已认证用户创建事件流订阅。
     *
     * @return 当前用户的新建事件流连接
     */
    @GetMapping(value = "/subscribe", produces = "text/event-stream")
    public SseEmitter subscribe() {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        return sseEmitterService.createEmitter(currentUserId);
    }
}
