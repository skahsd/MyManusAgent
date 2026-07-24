package cn.mymanus.manus.agent.prompt;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptManagement {

    @Value("${prompt.locale:/prompt}")
    private String locale;

    private static final Map<String, String> PROMPT_MAP = new ConcurrentHashMap<>();

    /**
     * 初始化操作，读取prompt目录下的所有文件，并将文件内容保存到PROMPT_MAP中
     *
     * @throws IOException 读取文件异常
     */
    @PostConstruct
    void init() throws IOException {
        String pattern = StrUtil.format("classpath:{}/*.txt", this.locale);
        PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
        Resource[] resources = loader.getResources(pattern);
        for (Resource resource : resources) {
            PROMPT_MAP.put(resource.getFilename(), IoUtil.readUtf8(resource.getInputStream()));
            log.info("加载prompt（{}）文件成功！", resource.getFilename());
        }
    }

    /**
     * 根据名称获取prompt内容
     *
     * @param name prompt名称
     * @return prompt内容
     */
    public String getPrompt(String name) {
        String key = StrUtil.endWith(name, ".txt") ? name : name + ".txt";
        return PROMPT_MAP.get(key);
    }
}
