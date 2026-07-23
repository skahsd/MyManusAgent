package cn.itcast.manus.message;

import cn.itcast.manus.dto.DialogMessageDTO;

public interface MessageSession {

    /**
     * 接收消息
     *
     * @param messageDTO 消息对象
     */
    void receiveMessages(DialogMessageDTO messageDTO);

    /**
     * 读取消息
     */
    String readMessage();

    /**
     * 发送消息
     */
    void sendMessage(String msg);

    /**
     * 发送消息
     */
    void sendMessage(DialogMessageDTO messageDTO);
}
