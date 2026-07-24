package cn.mymanus.manus.playwright;

import cn.mymanus.manus.agent.browser.PageSession;
import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Slf4j
@Configuration
public class PlaywrightComponentFactory {

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public PageSession pageSession(BrowserContext ctx) {
        return new PageSession(ctx);
    }
}

