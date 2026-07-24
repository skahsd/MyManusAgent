package cn.mymanus.manus.agent.table;

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
public class TableAgent extends BaseAgent {

    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    @Resource
    private PromptManagement promptManagement;
    @Resource
    private FileStorageService fileStorageService;
    private final MessageSession messageSession;

    public TableAgent(MessageSession messageSession) {
        this.messageSession = messageSession;
    }

    @Override
    protected String solve(String task) {
        //1. 调用大模型生成html内容
        var params = Map.of(Constant.TASK, task);
        var prompt = StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.TABLE), params);
        var html = this.chatModel.call(prompt);

        //2. 存储文件，生成下载地址
        var uuid = this.fileStorageService.saveFile(StrUtil.utf8Bytes(html));
        var url = this.fileStorageService.generateDownloadUrl("table.html", uuid);

        //3. 通过session发送信息给客户端
        var ms = DialogMessageDTO.builder()
                .text("[TableAgent]文件生成")
                .fileUrl(url)
                .build();
        this.messageSession.sendMessage(ms);

        //4. 返回内容给大模型
        var openurl = this.fileStorageService.generateOpenUrl(uuid);
        return StrUtil.format("""
                [TableAgent]
                生成可打开的url:{}
                生成的可下载url:{}
                """, openurl, url);
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }
}
