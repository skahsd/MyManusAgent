package cn.mymanus.manus.config;

import lombok.Data;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Data
@Configuration
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReActConfig {

    int maxStep = 100; // 最大步数
    int maxActionPerCall = 10; // 每次调用最多执行多少次动作
    boolean useVision = false; // 是否使用视觉

}