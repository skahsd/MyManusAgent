package cn.mymanus.manus.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.mymanus.manus.agent.AgentFactory;
import cn.mymanus.manus.dto.DialogMessageDTO;
import cn.mymanus.manus.enums.AgentTypeEnum;
import cn.mymanus.manus.message.WSSessionManagement;
import cn.mymanus.manus.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final WSSessionManagement wsSessionManagement;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void enhancedDialog(DialogMessageDTO message, SimpMessageHeaderAccessor headerAccessor) {
        if(StrUtil.isEmpty(message.getText())){
            log.info("receive empty message!!!");
            return;
        }

        // 获取会话id
        var sessionId = headerAccessor.getSessionId();
        // 根据会话id获取对话对象
        var wsSession = this.wsSessionManagement.sessionDialog(sessionId);
        // 将消息对象写入到会话中
        wsSession.receiveMessages(message);

        // 通过智能体进行处理业务
        var agent = AgentFactory.getAgent(AgentTypeEnum.RE_ACT_PLANNING_AGENT).apply(wsSession);
        Runnable src = () -> {
            try {
                // 获取客户端发来的消息
                var rawInput = wsSession.readMessage();
                // 通过规划agent进行执行
                var finalResult = agent.solveTask(rawInput);
                log.info("complete with final result:{}", finalResult);
                wsSession.sendMessage(finalResult);
                wsSession.sendMessage("任务流程结束");
            } finally {
                wsSessionManagement.clean(sessionId);
            }
        };
        executor.execute(src);
    }
}
