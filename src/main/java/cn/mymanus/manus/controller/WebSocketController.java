package cn.mymanus.manus.controller;

import cn.mymanus.manus.dto.DialogMessageDTO;
import cn.mymanus.manus.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 控制器，用于处理与 WebSocket 相关的消息路由和业务逻辑。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketService webSocketService;

    /**
     * 处理发送到 "/enhanced-dialog" 路径的消息。
     *
     * @param message        接收到的消息内容，封装为 `DialogMessageDTO` 对象
     * @param headerAccessor 提供对消息头部的访问，用于获取会话相关信息
     */
    @MessageMapping("/enhanced-dialog")
    public void enhancedDialog(@Payload DialogMessageDTO message, SimpMessageHeaderAccessor headerAccessor) {
        log.info("enhanced-dialog: {}", message);
        this.webSocketService.enhancedDialog(message, headerAccessor);
    }
}
