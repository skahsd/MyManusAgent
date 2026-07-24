package cn.mymanus.manus.agent.browser;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import cn.mymanus.manus.agent.ReActBaseAgent;
import cn.mymanus.manus.agent.prompt.PromptManagement;
import cn.mymanus.manus.constants.Constant;
import cn.mymanus.manus.message.MessageSession;
import cn.mymanus.manus.playwright.PlaywrightComponentFactory;
import cn.mymanus.manus.playwright.PlaywrightManagement;
import com.microsoft.playwright.BrowserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.Map;

@Slf4j
public class ReActBrowserAgent extends ReActBaseAgent {

    @Resource
    private PromptManagement promptManagement;
    @Resource
    private PlaywrightManagement playwrightManagement;
    @Resource
    private PlaywrightComponentFactory playwrightComponentFactory;

    private PageSession pageSession;

    public ReActBrowserAgent(MessageSession messageSession) {
        super(messageSession);
    }

    @Override
    public String reActSolve(String task) {
        return this.playwrightManagement
                .browserContextOperation(browserContext -> this.runWithContext(task, browserContext));
    }

    private String runWithContext(String task, BrowserContext ctx) {
        try {
            this.pageSession = this.playwrightComponentFactory.pageSession(ctx);
            var result = super.reActSolve(task);
            this.pageSession.cleanUp();
            return result;
        } catch (Exception e) {
            log.error("ReActBrowserAgent runWithContext error", e);
            return ExceptionUtil.getMessage(e);
        }
    }

    @Override
    protected List<ToolCallbackProvider> toolCallbackProvider() {
        return List.of(() -> ToolCallbacks.from(this.pageSession));
    }

    @Override
    protected String getCurrentStatus() {
        try {
            // 清空页面标志
            this.pageSession.clearFlag();
            // 获取当前页面状态
            return this.pageSession.getCurrentPageStatus(currentStep, super.reActConfig.getMaxStep());
        } catch (Exception e) {
            return "Exception in getCurrent page status:" + ExceptionUtil.getMessage(e);
        }
    }

    @Override
    protected void addInitInput(List<Message> inputList, String task) {
        // 添加系统消息
        inputList.add(SystemMessage.builder()
                .text(this.getPrompt(Constant.Prompts.BROWSER_SYSTEM, Map.of(Constant.MAX_ACTIONS, super.reActConfig.getMaxActionPerCall())))
                .build());

        // 添加用户消息
        inputList.add(UserMessage.builder()
                .text(this.getPrompt(Constant.Prompts.BROWSER_USER_TASK, Map.of(Constant.TASK, task)))
                .build());

        // 添加一个用户示例
        inputList.add(new UserMessage("下面是一个例子:"));

        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "1",
                "function",
                AGENT_OUTPUT_METHOD,
                "{'action': [{'clickElement': {'index': 0}}], 'current_state': {'evaluation_previous_goal': 'Success - 基于页面信息，我判断成功打开了某个页面', 'memory': '现在1/10的任务已完成', 'thinking': '接下来需要点击索引为0的元素'}}");
        inputList.add(new AssistantMessage("", Map.of(), List.of(toolCall)));
        inputList.add(new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("1", AGENT_OUTPUT_METHOD, "SUCCESS"))));
        inputList.add(new UserMessage("[以下是任务历史记录]"));
    }

    private String getPrompt(String name, Map<String, Object> params) {
        String prompt = this.promptManagement.getPrompt(name);
        return StrUtil.format(prompt, params);
    }

    @Override
    protected boolean isStatusSignificantChanged() {
        return this.pageSession.isStatusSignificantChanged();
    }
}
