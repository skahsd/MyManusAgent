package cn.mymanus.manus.message;

import cn.mymanus.manus.dto.DialogMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

@Slf4j
public class WSSession implements MessageSession {

    // 消息发送模板
    private final SimpMessagingTemplate messagingTemplate;
    // 消息缓存区，使用线程安全队列
    private final BlockingQueue<DialogMessageDTO> buffer = new ArrayBlockingQueue<>(1024);
    // 会话id提供者
    private final Supplier<String> sessionIdProvider;
    // 发送消息的目标
    private final String destination;

    public WSSession(Supplier<String> sessionIdProvider, String destination, SimpMessagingTemplate messagingTemplate) {
        this.sessionIdProvider = sessionIdProvider;
        this.destination = destination;
        this.messagingTemplate = messagingTemplate;
    }


    @Override
    public String readMessage() {
        try {
            return buffer.take().getText();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendMessage(String msg) {
        this.sendToClient(DialogMessageDTO.builder()
                .type(DialogMessageDTO.TYPE_SERVER)
                .text(msg)
                .build());
    }

    /**
     * 接收消息，将接受到的消息存储到缓冲区
     */
    @Override
    public void receiveMessages(DialogMessageDTO messageDTO) {
        try {
            buffer.put(messageDTO);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendMessage(DialogMessageDTO messageDTO) {
        messageDTO.setType(DialogMessageDTO.TYPE_SERVER);
        this.sendToClient(messageDTO);
    }

    /**
     * 将消息发送给客户端
     * <p>
     * 该方法负责将一个对话消息DTO（DialogMessageDTO）发送给特定客户端它通过获取当前会话ID（sessionId），
     * 创建适当的消息头，并使用Spring的MessagingTemplate将消息实际发送到客户端如果在发送过程中发生任何异常，
     * 它将捕获并记录错误
     *
     * @param messageDTO 包含要发送给客户端的消息信息的数据传输对象
     */
    private void sendToClient(DialogMessageDTO messageDTO) {
        try {
            // 获取当前会话的ID
            String sessionId = this.sessionIdProvider.get();

            // 创建一个下行消息头，用于发送消息到客户端
            SimpMessageHeaderAccessor downHeader = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            // 设置消息头的会话ID
            downHeader.setSessionId(sessionId);
            // 允许底层消息头被修改
            downHeader.setLeaveMutable(true);

            // 使用MessagingTemplate将消息发送到特定用户
            this.messagingTemplate.convertAndSendToUser(sessionId, destination, messageDTO, downHeader.getMessageHeaders());
        } catch (Exception e) {
            // 记录在发送消息过程中发生的异常
            log.error("Exception in sendToClient", e);
        }
    }

}