package cn.mymanus.manus.playwright;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType.LaunchPersistentContextOptions;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * 封装了 Playwright 管理
 */
@Getter
@Component
public class PlaywrightManagement {

    // Playwright 实例，用于创建和管理浏览器
    private Playwright playwright;

    public <T> T browserContextOperation(Function<BrowserContext, T> func) {
        // 默认使用路径 /tmp/playwrightContext 和非无头模式启动 Chromium
        LaunchPersistentContextOptions op = new LaunchPersistentContextOptions();
        op.setHeadless(false);
        Path path = new File("/tmp/playwrightContext").toPath();

        // 封装了 try-with-resources 模式自动关闭浏览器资源
        try (var ctx = this.playwright.chromium().launchPersistentContext(path, op)) {
            return func.apply(ctx);
        }
    }

    /**
     * 在 Bean 初始化时创建 Playwright 实例
     */
    @PostConstruct
    void init() {
        this.playwright = Playwright.create();
    }

    /**
     * 在 Bean 销毁前关闭 Playwright，释放资源
     */
    @PreDestroy
    void destroy() {
        this.playwright.close();
    }
}