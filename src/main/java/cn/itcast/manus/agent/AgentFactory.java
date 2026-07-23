package cn.itcast.manus.agent;

import cn.itcast.manus.agent.planning.ReActPlanningAgent;
import cn.itcast.manus.enums.AgentTypeEnum;
import cn.itcast.manus.message.MessageSession;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class AgentFactory {

    public static final Map<AgentTypeEnum, Function<MessageSession, Agent>> AGENT_FUNC_MAP = new HashMap<>();

    /**
     * 初始化方法，完成Agent的注册
     */
    @PostConstruct
    public void init() {
        // 注册任务规划智能体
        AGENT_FUNC_MAP.put(AgentTypeEnum.RE_ACT_PLANNING_AGENT, this::reActPlanningAgent);
    }

    /**
     * 根据agentTypeEnum获取对应的Agent
     */
    public static Function<MessageSession, Agent> getAgent(AgentTypeEnum agentTypeEnum) {
        Function<MessageSession, Agent> fun = AGENT_FUNC_MAP.get(agentTypeEnum);
        if (null == fun) {
            throw new IllegalArgumentException("找不到对应的智能体: " + agentTypeEnum);
        }
        return fun;
    }

    /**
     * 任务规划智能体，交由Spring管理
     *
     * @param messageSession 会话对象
     * @return 查找到的智能体实例
     */
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Agent reActPlanningAgent(MessageSession messageSession) {
        return new ReActPlanningAgent(messageSession);
    }

}
