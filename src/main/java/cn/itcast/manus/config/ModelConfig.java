package cn.itcast.manus.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

/**
 * ModelConfig 是一个配置类，用于定义和创建不同的 ChatModel Bean。
 * 主要包括主Agent模型 (mainAgentModel) 和计划Agent模型 (planModel)。
 */
@Configuration
public class ModelConfig {

    /**
     * 常量定义：
     * - MAIN_AGENT: 主Agent模型的 Bean 名称
     * - PLAN_AGENT: 计划Agent模型的 Bean 名称
     * - MAIN_AGENT_MODEL_CONFIG: 主Agent模型配置的 Bean 名称
     */
    public static final String MAIN_AGENT = "mainAgentModel";
    public static final String PLAN_AGENT = "planModel";
    public static final String MAIN_AGENT_MODEL_CONFIG = "mainAgentModelConfig";

    /**
     * 创建主Agent模型 (mainAgentModel) 的 Bean。
     * 使用 mainAgentModelConfig() 方法提供的配置来构建模型。
     *
     * @return ChatModel 实例
     */
    @Bean
    @Primary
    public ChatModel mainAgentModel() {
        return buildByConfig(mainAgentModelConfig());
    }

    /**
     * 创建计划Agent模型 (planModel) 的 Bean。
     * 使用 planModelConfig() 方法提供的配置来构建模型。
     *
     * @return ChatModel 实例
     */
    @Bean
    public ChatModel planModel() {
        return buildByConfig(planModelConfig());
    }

    /**
     * 根据给定的 BaseModelConfig 配置构建 ChatModel 实例。
     *
     * @param cfg BaseModelConfig 对象，包含模型的基本配置信息
     * @return 构建好的 ChatModel 实例
     */
    private ChatModel buildByConfig(BaseModelConfig cfg) {
        var bd = OpenAiApi.builder()
                .baseUrl(cfg.getBaseUrl())  // 设置 OpenAI API 的基础 URL
                .apiKey(new SimpleApiKey(cfg.getApiKey()));  // 设置 API 密钥

        // 如果 completionsPath 存在且非空，则设置到 OpenAiApi 构建器中
        Optional.ofNullable(cfg.getCompletionsPath())
                .filter(StringUtils::isNotBlank)
                .ifPresent(bd::completionsPath);

        // 构建 OpenAiChatOptions，指定模型名称
        var option = OpenAiChatOptions.builder()
                .model(cfg.getModelName())
                .build();

        // 返回构建好的 ChatModel 实例
        return OpenAiChatModel.builder()
                .openAiApi(bd.build())  // 设置 OpenAiApi 实例
                .defaultOptions(option)  // 设置默认的 ChatOptions
                .build();
    }

    /**
     * 创建 planModel 的配置 Bean，使用前缀为 "plan-model" 的配置属性。
     *
     * @return BaseModelConfig 实例
     */
    @Bean
    @ConfigurationProperties(prefix = "plan-model")
    public BaseModelConfig planModelConfig() {
        return new BaseModelConfig();
    }

    /**
     * 创建 mainAgentModel 的配置 Bean，使用前缀为 "agent-model" 的配置属性。
     *
     * @return BaseModelConfig 实例
     */
    @Bean
    @ConfigurationProperties(prefix = "agent-model")
    public BaseModelConfig mainAgentModelConfig() {
        return new BaseModelConfig();
    }

    /**
     * BaseModelConfig 是一个内部静态类，用于封装模型的基本配置信息。
     * 包括基础 URL、API 密钥、模型名称、补全路径和是否启用流式响应。
     */
    @Data
    public static class BaseModelConfig {
        String baseUrl;         // OpenAI API 的基础 URL
        String apiKey;          // API 密钥
        String modelName;       // 模型名称
        String completionsPath; // 补全路径（可选）
        boolean stream;         // 是否启用流式响应
    }
}
