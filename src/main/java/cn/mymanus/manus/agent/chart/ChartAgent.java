package cn.mymanus.manus.agent.chart;

import cn.hutool.core.util.StrUtil;
import cn.mymanus.manus.agent.BaseAgent;
import cn.mymanus.manus.agent.prompt.PromptManagement;
import cn.mymanus.manus.config.ModelConfig;
import cn.mymanus.manus.constants.Constant;
import cn.mymanus.manus.dto.DialogMessageDTO;
import cn.mymanus.manus.message.MessageSession;
import cn.mymanus.manus.service.FileStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

@Slf4j
public class ChartAgent extends BaseAgent {

    private final MessageSession messageSession;

    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    @Resource
    private PromptManagement promptManagement;
    @Resource
    private FileStorageService fileStorageService;

    public ChartAgent(MessageSession messageSession) {
        this.messageSession = messageSession;
    }

    @Override
    protected String solve(String task) {
        // 1.LLM生成 html
        var params = Map.of(Constant.TASK, task);
        var prompt = StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.CHART), params);
        var html = this.chatModel.call(prompt);

        // 2.存储文件，生成下载地址
        var uuid = this.fileStorageService.saveFile(StrUtil.utf8Bytes(html));
        var url = this.fileStorageService.generateDownloadUrl("chart.html", uuid);

        // 3.走session返回到message里
        var ms = DialogMessageDTO.builder()
                .text("[ChartAgent]文件生成")
                .fileUrl(url)
                .build();
        this.messageSession.sendMessage(ms);

        return StrUtil.format("""
                [ChartAgent]
                生成可打开的url:{}
                生成的可下载url:{}
                """, this.fileStorageService.generateOpenUrl(uuid), url);
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }
}