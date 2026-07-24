package cn.mymanus.manus.service;

import cn.mymanus.manus.dto.DialogMessageDTO;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

public interface WebSocketService {

    /**
     * 对话处理
     *
     * @param message        消息数据
     * @param headerAccessor 消息头
     */
    void enhancedDialog(DialogMessageDTO message, SimpMessageHeaderAccessor headerAccessor);

}
