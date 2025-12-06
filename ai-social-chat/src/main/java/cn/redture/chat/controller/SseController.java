package cn.redture.chat.controller;

import cn.redture.chat.sse.SseEmitterService;
import cn.redture.common.util.SecurityContextHolderUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/sse")
public class SseController {

    @Resource
    private SseEmitterService sseEmitterService;

    @GetMapping(value = "/subscribe", produces = "text/event-stream")
    public SseEmitter subscribe() {
        Long currentUserId = SecurityContextHolderUtil.getUserId();
        return sseEmitterService.createEmitter(currentUserId);
    }
}
