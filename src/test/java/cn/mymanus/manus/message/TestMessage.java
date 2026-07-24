package cn.mymanus.manus.message;

import cn.mymanus.manus.dto.DialogMessageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WSSessionTest {

    private WSSession wsSession;
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        // Mock 依赖
        messagingTemplate = mock(SimpMessagingTemplate.class);

        // 手动创建 WSSession（它不是 Spring Bean）
        wsSession = new WSSession(
                () -> "test-session-id",   // Supplier<String> sessionIdProvider
                "/queue/notify",            // String destination
                messagingTemplate           // SimpMessagingTemplate
        );
    }

    @Test
    @DisplayName("测试接收消息并存入缓冲区")
    void testReceiveMessages() {
        // 准备数据
        DialogMessageDTO message = DialogMessageDTO.builder()
                .text("测试")
                .imageUrl("123")
                .fileUrl("1233")
                .openUrl("12333")
                .build();

        // 执行
        wsSession.receiveMessages(message);

        // 验证：消息能正常读取
        String result = wsSession.readMessage();
        assertEquals("测试", result);
    }

//    @Test
//    @DisplayName("测试发送消息")
//    void testSendMessage() {
//        // 执行
//        wsSession.sendMessage("你好");
//
//        // 验证 messagingTemplate 被调用
//        verify(messagingTemplate, times(1))
//                .convertAndSendToUser(
//                        eq("test-session-id"),
//                        eq("/queue/notify"),
//                        any(DialogMessageDTO.class),
//                        any()
//                );
//    }
}