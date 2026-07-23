package cn.itcast.manus.agent.planning;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.agent.ReActBaseAgent;
import cn.itcast.manus.agent.prompt.PromptManagement;
import cn.itcast.manus.config.ModelConfig;
import cn.itcast.manus.constants.Constant;
import cn.itcast.manus.message.MessageSession;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class ReActPlanningAgent extends ReActBaseAgent {

    @Resource
    private PromptManagement promptManagement;
    @Resource(name = ModelConfig.PLAN_AGENT)
    private ChatModel chatModel;
    // 存放agent，key：agentName，value：功能描述
    private final Map<String, String> agentInfo = new HashMap<>();
    // 线程安全的动态数组，用来存储子任务链
    private final CopyOnWriteArrayList<SubTaskNode> subTaskChain = new CopyOnWriteArrayList<>();

    public ReActPlanningAgent(MessageSession messageSession) {
        super(messageSession);
    }

    @Override
    public String reActSolve(String task) {
        this.subTaskChain.clear();
        return super.reActSolve(task);
    }

    @Override
    protected String getCurrentStatus() {
        //TODO 获取任务链中的最后一个任务，查找到对应的Agent进行执行

        // 将子任务链中的结果以字符串拼接，拼接格式为：子任务1->子任务2->子任务3
        var chainStr = subTaskChain.stream()
                .map(SubTaskNode::strInStatus)
                .reduce((x, y) -> x + "->" + y)
                .orElse("[Empty]");

        // 将子任务链中最后一个任务的结果拼接
        var latestStr = subTaskChain.stream()
                .reduce((x, y) -> y)
                .map(SubTaskNode::getResult)
                .orElse("[Empty]");

        // 将结果拼接到模板中，作为下次调用大模型的历史对话
        var params = Map.of(
                "stepData", StrUtil.format("{}/{}", super.currentStep, super.reActConfig.getMaxStep()),
                "dateTime", DateUtil.now(),
                "subTaskChain", chainStr,
                "latestResult", latestStr);
        return StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.PLANNING_STATUS), params);
    }

    @Override
    protected boolean isStatusSignificantChanged() {
        // 计划Agent每次的action只需要执行一个，后面的action不再执行
        return true;
    }

    @Override
    protected void addInitInput(List<Message> inputList, String task) {
        // 添加系统消息
        inputList.add(SystemMessage.builder()
                .text(promptManagement.getPrompt(Constant.Prompts.PLANNING_SYSTEM))
                .build());

        // 添加用户消息
        inputList.add(UserMessage.builder()
                .text(this.getPromptPlanningUserTask(task))
                .build());

        // 添加说明
        inputList.add(UserMessage.builder()
                .text("[你的历史记忆从此处开始]")
                .build());
    }

    private String getPromptPlanningUserTask(String task) {
        var promptPlanningSystem = this.promptManagement.getPrompt(Constant.Prompts.PLANNING_USER_TASK);
        var param = Map.of(
                Constant.TASK, task,
                Constant.AGENT_DATA, String.valueOf(agentInfo));
        return StrUtil.format(promptPlanningSystem, param);
    }

    @Tool(description = "规划下一个子任务节点")
    public String generateNext(@ToolParam(description = "agent名称") String agent,
                               @ToolParam(description = "子任务内容") String subTask,
                               @ToolParam(description = "完成任务的最大步数") int maxStep) {
        var res = subTaskChain.stream()
                .reduce((x, y) -> y) // 获取最后一个元素
                .map(p -> { // 对最后一个任务进行描述，生成了新的子任务
                    var result = "子任务{}的执行结果:\n{}\n基于此结果成功规划下一个子任务节点:{}";
                    return StrUtil.format(result, p.getSubTask(), p.getResult(), subTask);
                })
                // 没有子任务，就生成新的任务
                .orElseGet(() -> StrUtil.format("成功规划下一个子任务节点:{}", subTask));

        // 生成新的子任务节点
        SubTaskNode subTaskNode = SubTaskNode.builder()
                .agent(agent)
                .subTask(subTask)
                .maxStep(maxStep)
                .build();
        // 将子任务加入到任务链中
        this.subTaskChain.add(subTaskNode);
        return res;
    }

    @Override
    protected List<ToolCallbackProvider> toolCallbackProvider() {
        // 将当前对象中标记为@Tool解析出来，作为工具回调
        return List.of(() -> ToolCallbacks.from(ReActPlanningAgent.this));
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskNode {
        private String agent;
        private String subTask;
        private int maxStep;
        private String result;
        private int resultStep = -1;

        public String strInStatus() {
            return StrUtil.format("[{},Task:{},Result:{},resultStep:{}]",
                    agent,
                    subTask,
                    StrUtil.truncateUtf8(result, 20),
                    resultStep);
        }
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }
}
