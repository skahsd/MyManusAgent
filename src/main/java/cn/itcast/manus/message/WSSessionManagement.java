package cn.itcast.manus.message;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WSSessionManagement {

    // 基本的消息发送模板
    public final SimpMessagingTemplate messagingTemplate;

    // 定义会话对象，来用存储会话信息，key为会话ID，value为会话对象
    private final Map<String, MessageSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * 通过会话id获取会话对象
     */
    public MessageSession sessionDialog(String sessionId) {
        return sessionMap.computeIfAbsent(sessionId, k -> new WSSession(() -> k, "/queue/dialog", this.messagingTemplate));
    }

    /**
     * 清理会话对象
     *
     * @param sessionId 会话ID
     */
    public void clean(String sessionId) {
        this.sessionMap.remove(sessionId);
    }
}