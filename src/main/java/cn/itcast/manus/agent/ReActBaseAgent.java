package cn.itcast.manus.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.itcast.manus.agent.bean.ReActInput;
import cn.itcast.manus.config.ModelConfig;
import cn.itcast.manus.config.ReActConfig;
import cn.itcast.manus.message.MessageSession;
import cn.itcast.manus.util.JsonFinder;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@Slf4j
public abstract class ReActBaseAgent extends BaseAgent {

    public static final String AGENT_OUTPUT_METHOD = "AgentOutput";

    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    @Resource
    protected ReActConfig reActConfig;
    @Resource(name = ModelConfig.MAIN_AGENT_MODEL_CONFIG)
    private ModelConfig.BaseModelConfig modelConfig;

    @Getter
    protected int currentStep = 1;
    // 是否停止标识
    private boolean agentStop = false;
    // 最终结果
    private String finalResult;

    protected MessageSession messageSession;
    // 工具定义结构内容
    private String toolSchema;
    // 工具定义
    private DefaultToolDefinition toolDefinition;
    // 定义动作映射，key为动作名称，value为动作执行函数（key：参数，value：执行返回值）
    private final Map<String, Function<Map<String, Object>, String>> actionRegistry = new HashMap<>();

    public ReActBaseAgent(MessageSession messageSession) {
        this(messageSession, "schema/schemaBaseReAct.json");
    }

    public ReActBaseAgent(MessageSession messageSession, String toolSchemaPath) {
        this.messageSession = messageSession;
        this.toolSchema = ResourceUtil.readStr(toolSchemaPath, StandardCharsets.UTF_8);
        this.refreshToolDefinition();
    }

    @Override
    protected String solve(String task) {
        return this.reActSolve(task);
    }

    public String reActSolve(String task) {
        // 初始化当前步骤
        this.currentStep = 1;
        // 初始化动作列表
        this.initActionMap();
        // 定义输入消息列表
        List<Message> inputList = new LinkedList<>();
        // 将当前任务加入到列表中
        this.addInitInput(inputList, task);

        // 定义 token 计数器
        var tokenCount = new AtomicLong(0L);

        // 如果没有标记为停止, 并且当前的步骤小于等于最大步骤，则进行循环
        while (!this.agentStop && this.currentStep <= this.reActConfig.getMaxStep()) {
            this.buildCurrentParse(inputList);
            this.callingLLM(inputList, tokenCount);
            log.info("step:{},tokenCount:{}", this.currentStep, tokenCount.get());
            log.info("[{}👆]------------------", getClass().getSimpleName());
            this.currentStep++;
        }

        // 最后一步检查
        if (this.currentStep >= this.reActConfig.getMaxStep()) {
            String text = """
                    注意，你的执行已达到最大步数限制，请立即使用done操作返回截至到目前的工作成果，
                    需要包含具体的内容，不允许任何省略，最后总结尚未完成的任务并对遇到的问题加以说明
                    """;
            inputList.add(new UserMessage(text));
            this.callingLLM(inputList, tokenCount);
        }
        return finalResult;
    }

    protected void refreshToolDefinition() {
        JSONObject jsonObject = JSONUtil.parseObj(this.toolSchema);
        this.toolDefinition = new DefaultToolDefinition(
                jsonObject.getStr("name"),
                jsonObject.getStr("description"),
                jsonObject.getStr("inputSchema"));
    }

    /**
     * 调用 LLM
     */
    private void callingLLM(List<Message> inputList, AtomicLong tokenCount) {
        ChatResponse chatResponse;
        try {
            // 构造请求参数
            OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                    .toolCallbacks(this.getToolCallbacks()) // 添加工具回调
                    .internalToolExecutionEnabled(false) // 设置为false，自行控制对tool的调用过程
                    // 强制设置工具名
                    .toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.FUNCTION(AGENT_OUTPUT_METHOD))
                    .build();
            // 定义 Prompt
            Prompt req = new Prompt(inputList, chatOptions);
            // 调用大模型
            if (modelConfig.isStream()) {
                // 流式调用
                chatResponse = this.chatModel()
                        .stream(req)
                        .collectList()
                        .block()
                        .stream()
                        .findFirst()
                        .orElseThrow();
            } else {
                // 非流式调用
                chatResponse = this.chatModel().call(req);
            }
        } catch (Exception e) {
            log.error("调用AI大模型时出错！", e);
            throw new RuntimeException(e);
        }

        // 从响应中提取token的使用数，累加到tokenCount中
        Optional.of(chatResponse)
                .map(ChatResponse::getMetadata)
                .map(ChatResponseMetadata::getUsage)
                .map(Usage::getTotalTokens)
                .ifPresent(tokenCount::addAndGet);

        // 提取出大模型的响应内容，进行后续的处理
        Optional.of(chatResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .ifPresent(assistantMessage -> this.processResponse(assistantMessage, inputList));
    }

    private void processResponse(AssistantMessage assistantMessage, List<Message> inputList) {
        var text = assistantMessage.getText();
        log.info("开始处理大模型的响应，内容：{}", text);
        if (assistantMessage.hasToolCalls()) {
            if (assistantMessage.getToolCalls().size() > 1) {
                log.warn("找到1个以上的工具执行请求列表:{}", assistantMessage.getToolCalls());
            }

            var tool = CollUtil.getFirst(assistantMessage.getToolCalls());
            if (StrUtil.equalsIgnoreCase(tool.name(), AGENT_OUTPUT_METHOD)) {
                var reActInput = JSONUtil.toBean(tool.arguments(), ReActInput.class);
                this.agentOutput(reActInput, inputList, assistantMessage);
            } else {
                log.error("未知工具名称:{}", tool.name());
            }
            return;
        }

        // 如果没有工具调用，则尝试从响应中解析出ReActInput
        JsonFinder.findFirstJson(text)
                .map(json -> JSONUtil.toBean(json, ReActInput.class))
                .ifPresent(reActInput -> this.agentOutput(reActInput, inputList, assistantMessage));
    }

    private void agentOutput(ReActInput reActInput, List<Message> inputList, AssistantMessage assistantMessage) {
        var status = reActInput.getStatus();
        log.info("🏹 previous goal:{}", status.getEvaluationPreviousGoal());
        log.info("💾 memory:{}", status.getMemory());
        log.info("🧠 thinking:{}", status.getThinking());

        // 向客户端发送消息
        this.messageSession.sendMessage(String.format("[%s]%s", this.name(), status.getThinking()));

        // 获取动作列表
        var srcActionList = reActInput.getAction();
        log.info("🛠 action:{}", reActInput.getAction());

        var processedActionList = new LinkedList<Map<String, Object>>();
        var resultBuilder = new StringBuilder();

        // 1.run all action
        for (var action : srcActionList) {
            processedActionList.add(action);
            var actName = action.keySet().stream().findFirst().orElseThrow();
            try {
                var actionResult = actionRegistry.get(actName).apply(BeanUtil.beanToMap(action.get(actName)));
                resultBuilder.append(action).append("->").append(actionResult).append('\n');
            } catch (Exception e) {
                log.error("error in running action:{}", action, e);
                // break loop
                resultBuilder.append(action).append("->").append("Exception:").append(StrUtil.truncateUtf8(e.getMessage(), 32)).append('\n');
                break;
            }
            if (srcActionList.indexOf(action) != srcActionList.size() - 1 && this.isStatusSignificantChanged()) {
                // 如果当前动作不是最后一个动作，并且状态发生了显著变化，则通过 break 提前终止循环。
                break;
            }
        }

        if (processedActionList.size() < srcActionList.size()) {
            // 如果已经执行的动作数量小于总动作数量，则说明状态发生了显著变化，需要忽略已经执行的动作。
            // 注意：前面的操作会导致上下文发生重大变化，忽略以下操作：
            resultBuilder.append("NOTICE:previous action leads a significant change on context,ignore following action:");
            var ignore = new ArrayList<>(srcActionList);
            ignore.removeAll(processedActionList);
            ignore.forEach(act -> {
                resultBuilder.append(act);
                resultBuilder.append('\n');
            });
            // 您应该根据最新状态继续操作
            resultBuilder.append("You should continue you step based on the newest status");
        }

        // 2.得到结果
        var actionResult = resultBuilder.toString();
        log.info("📃 obs:{}", actionResult);

        // 3.按当前结果重建消息列表（历史部分）
        // 删除最新的聊天消息（UserMessage）
        inputList.remove(inputList.size() - 1);
        // 把响应加入到输入列表中
        inputList.add(assistantMessage);
        if (assistantMessage.hasToolCalls()) {
            var toolCall = CollUtil.getFirst(assistantMessage.getToolCalls());
            // 添加工具消息响应
            var toolResponseMessage = new ToolResponseMessage(
                    List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), "success")));
            inputList.add(toolResponseMessage);
        }
        inputList.add(new UserMessage(String.format("思路'%s'的行动结果如下:\n%s", status.getThinking(), actionResult)));
    }

    protected boolean isStatusSignificantChanged() {
        return false;
    }

    private List<ToolCallback> getToolCallbacks() {
        return List.of(new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException("toolCalling should controlled by native program.");
            }
        });
    }

    private void buildCurrentParse(List<Message> inputList) {
        inputList.add(new UserMessage(this.getCurrentStatus()));
    }

    /**
     * 获取任务当前的状态数据，使用L2M规则每次放入上下文的最末端
     *
     * @return
     */
    protected abstract String getCurrentStatus();

    /**
     * 1.增加SystemMessage
     * 2.增加主任务对应的UserMessage
     * 3.增加One-Shot Example（可选）
     */
    protected abstract void addInitInput(List<Message> inputList, String task);

    /**
     * 初始action化方法绑定，应与构造参数中传入的schema匹配
     */
    protected void initActionMap() {
        actionRegistry.put("done", paramMap -> {
            var result = MapUtil.getStr(paramMap, "text");
            this.done(result);
            return result;
        });
        toolCallbackProvider().forEach(this::mergeByToolCallbackProvider);
    }

    protected List<ToolCallbackProvider> toolCallbackProvider() {
        return List.of();
    }

    /**
     * 将已有Tool的定义并入ReAct的Schema中，例如MCP服务提供的能力
     */
    private void mergeByToolCallbackProvider(ToolCallbackProvider toolCallback) {
        // 读取原始的工具定义
        JSONObject jsonObject = JSONUtil.parseObj(this.toolSchema);
        String expression = "inputSchema.properties.action.items.properties";
        // 读取具体工具的定义
        JSONObject actionEntry = jsonObject.getByPath(expression, JSONObject.class);
        for (ToolCallback tool : toolCallback.getToolCallbacks()) {
            var def = tool.getToolDefinition();
            // 将子类中定义的工具加入到工具定义中
            actionEntry.set(def.name(), JSONUtil.parseObj(def.inputSchema()));
            actionRegistry.put(def.name(), mp -> tool.call(JSONUtil.toJsonStr(mp)));
        }
        // 回写到原始对象中
        jsonObject.putByPath(expression, actionEntry);
        this.toolSchema = jsonObject.toString();
        this.refreshToolDefinition();
    }

    /**
     * 完成动作
     */
    protected void done(String result) {
        this.agentStop = true;
        this.finalResult = result;
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }

}