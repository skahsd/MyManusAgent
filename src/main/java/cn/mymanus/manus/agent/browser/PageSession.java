package cn.mymanus.manus.agent.browser;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.mymanus.manus.agent.prompt.PromptManagement;
import cn.mymanus.manus.constants.Constant;
import cn.mymanus.manus.service.PageContentExtractService;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserContext.WaitForPageOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForLoadStateOptions;
import com.microsoft.playwright.Page.WaitForSelectorOptions;
import com.microsoft.playwright.Page.WaitForURLOptions;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.Resource;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 对于 Playwright 的页面会话，用于管理页面的导航、动作和元素分析等操作。
 */
@Slf4j
public class PageSession {

    private static final String PLAYWRIGHT_CONTAINER_REMOVE = "document.getElementById(\"playwright-highlight-container\")?.remove()";
    private static final String PLAYWRIGHT_DATATESTID_REMOVE = """
            document.querySelectorAll('*').forEach(element => {
                    if (element.hasAttribute('data-testid')) {
                      element.removeAttribute('data-testid');
                    }
                  });
            """;
    public static final String AMOUNT = "amount";
    private static final String HIGHLIGHT_INDEX = "highlightIndex";
    // 等待超时时间，单位为毫秒
    private static final long WAIT_TIMEOUT = 5000L;

    @Resource
    private PromptManagement promptManagement;
    @Resource
    private PageContentExtractService pageContentExtractService;
    private final Map<Integer, JSONObject> allElement = new TreeMap<>();
    private Integer rootId;
    private final String buildDomJS;
    private final Set<String> attributeKey;
    private final BrowserContext browserContext;
    private final List<Page> openInOperation = new LinkedList<>();
    @Setter
    @Getter
    private int pageIndex;
    @Getter
    private String pageListInfo;
    private int flagNavi = 0;
    private int flagNewPage = 0;
    private int flagTabChanged = 0;
    private String originalContent;

    public PageSession(BrowserContext browserContext) {
        this.browserContext = browserContext;
        this.pageIndex = 0;
        this.attributeKey = Set.of("title", "type", "name", "role", "tabindex", "aria-label", "placeholder", "value", "alt", "aria-expanded", "class");
        this.buildDomJS = ResourceUtil.readUtf8Str("js/buildDomTree.js");
    }

    /**
     * 清除页面状态标志
     */
    public void clearFlag() {
        this.flagNavi = 0;
        this.flagNewPage = 0;
        this.flagTabChanged = 0;
    }

    /**
     * 刷新页面信息，包括当前页面的标题、URL
     */
    private void refreshPageInfo() {
        var pageListInfo = new StringBuilder();
        var page = listPageWithoutExtension();
        var pageArr = page.toArray(Page[]::new);
        var lst = Stream.iterate(0, i -> i + 1).limit(pageArr.length)
                .map(i -> new BrowserTabInfo(i, pageArr[i].title(), pageArr[i].url()))
                .toList();
        pageListInfo.append(JSONUtil.toJsonStr(lst)).append("\n");
        pageListInfo.append("current at index:").append(this.pageIndex).append("\n");
        pageListInfo.append("using 'switchTab' action to change browser tab\n");
        this.pageListInfo = pageListInfo.toString();
        this.originalContent = currentPage().content();
    }

    /**
     * 是否发生异常情况
     */
    public boolean isStatusSignificantChanged() {
        if (flagNavi != 0 || flagNewPage != 0 || flagTabChanged != 0) {
            return true;
        }
        try {
            var page = currentPage();
            var opt = new WaitForSelectorOptions();
            opt.setTimeout(WAIT_TIMEOUT);

            var handle = page.waitForSelector("#playwright-highlight-container", opt);
            return handle == null;
        } catch (Exception e) {
            log.trace("significantChanged by exception", e);
            return true;
        }
    }

    private void reAnalyze() {
        this.refreshPageInfo();
        this.removeAnalyzeContainer();
        var page = currentPage();

        var result = page.evaluate(buildDomJS);
        var jo = JSONUtil.parseObj(result);
        var map = jo.getJSONObject("map");
        // 根节点id
        this.rootId = jo.getInt("rootId");
        // 将所有的节点信息写入到allElement中
        map.forEach((k, v) -> allElement.put(Convert.toInt(k), (JSONObject) v));
    }

    /**
     * 等待新页面的出现，超时时间为 3 秒。
     */
    private Optional<Page> waitForNewPage() {
        // waiting for new page
        try {
            var opt = new WaitForPageOptions();
            opt.setTimeout(WAIT_TIMEOUT);
            var newPage = this.browserContext.waitForPage(opt, () -> {});
            this.waitPageIdle(newPage);
            this.flagNewPage = 1;
            return Optional.of(newPage);
        } catch (TimeoutError e) {
            log.trace("no page found after waiting 5 s");
            return Optional.empty();
        }
    }

    /**
     * 等待页面加载完成，超时时间为 5 秒
     */
    private void waitPageIdle(Page p) {
        try {
            p.waitForLoadState();
            var opt = new WaitForLoadStateOptions();
            opt.setTimeout(WAIT_TIMEOUT);
            p.waitForLoadState(LoadState.NETWORKIDLE, opt);
        } catch (TimeoutError e) {
            log.trace("ignore waiting loadstatus timeout.", e);
        }
    }

    /**
     * 获取当前页面对象
     */
    private Page currentPage() {
        return this.listPageWithoutExtension().get(pageIndex);
    }

    /**
     * 列出所有非扩展页面
     */
    private List<Page> listPageWithoutExtension() {
        return browserContext.pages().stream()
                .filter(p -> !p.url().toLowerCase().startsWith("chrome-extension"))
                .toList();
    }

    /**
     * 移除页面中的分析容器
     */
    public void removeAnalyzeContainer() {
        var page = this.listPageWithoutExtension();
        page.forEach(p -> {
            p.evaluate(PLAYWRIGHT_CONTAINER_REMOVE);
            p.evaluate(PLAYWRIGHT_DATATESTID_REMOVE);
        });
    }

    /**
     * 清理页面资源，关闭打开的页面
     */
    public void cleanUp() {
        this.removeAnalyzeContainer();
        this.openInOperation.forEach(Page::close);
    }



    /**
     * 等待页面导航完成
     */
    private boolean waitNavi(Page page, String oldUrl) {
        try {
            var waitForURLOptions = new WaitForURLOptions();
            waitForURLOptions.setTimeout(WAIT_TIMEOUT);
            Predicate<String> urlChanged = s -> !s.equalsIgnoreCase(oldUrl);
            page.waitForURL(urlChanged, waitForURLOptions);
            this.flagNavi = 1;
            return true;
        } catch (TimeoutError e) {
            log.trace("ignore waiting loadstatus timeout.", e);
            return false;
        }
    }

    /**
     * 定位到指定页面，并更新页面索引
     */
    private void locateOn(Page page) {
        this.pageIndex = listPageWithoutExtension().indexOf(page);
        this.openInOperation.add(page);
    }



    /**
     * 向上滚动页面
     */
    public String scrollUp(Map<String, Object> arg) {
        var page = currentPage();
        int amount = Convert.toInt(arg.get(AMOUNT));
        page.evaluate(String.format("window.scrollBy(0, -%s);", amount));
        return "SUCCESS";
    }

    /**
     * 向下滚动页面
     */
    public String scrollDown(Map<String, Object> arg) {
        var page = currentPage();
        int amount = Convert.toInt(arg.get(AMOUNT));
        page.evaluate(String.format("window.scrollBy(0, %s);", amount));
        return "SUCCESS";
    }


    /**
     * 获取当前页面的状态信息，包括 URL、页面数据、滚动信息等
     */
    public String getCurrentPageStatus(int currentStep, int maxStep) {
        this.reAnalyze();
        var page = this.currentPage();
        var params = new HashMap<>();
        params.put("url", page.url());
        params.put("pageListInfo", getPageListInfo());
        var pageData = fromAllElementByTree(rootId);
        params.put("pageData", pageData);
        params.put("stepData", String.format("%s/%s", currentStep, maxStep));
        params.put("dateTime", String.valueOf(new Date()));

        var sc = this.getScrollInfo();
        params.put("scrollData",
                String.format(
                        "...%s pixels above,%s pixels below,using extract_content action to see more information...",
                        sc.getPixelsAbove(), sc.getPixelsBelow()));

        return StrUtil.format(promptManagement.getPrompt(Constant.Prompts.PAGE_STATUS), params);
    }

    private boolean containsHighlighted(Integer indexId) {
        var node = this.allElement.get(indexId);
        if (node.containsKey(HIGHLIGHT_INDEX)) {
            return true;
        }
        return childIdxList(node).stream().anyMatch(this::containsHighlighted);
    }

    private List<Integer> childIdxList(JSONObject node) {
        return Optional.of(node)
                .filter(j -> j.containsKey("children"))
                .map(j -> j.getJSONArray("children")
                        .stream()
                        .map(Convert::toInt)
                        .toList())
                .orElse(Collections.emptyList());
    }

    /**
     * 递归生成页面元素树的字符串
     */
    private String fromAllElementByTree(Integer indexId) {
        var node = this.allElement.get(indexId);
        var childList = this.childIdxList(node);
        var childContent = childList.stream()
                .map(this::fromAllElementByTree)
                .filter(StrUtil::isNotBlank)
                .reduce((x, y) -> x + "\n" + y)
                .orElse(StrUtil.EMPTY);
        if (!node.getBool("isVisible", false)) {
            return childContent;
        }
        if ("TEXT_NODE".equals(node.getStr("type"))) {
            return node.getStr("text") + "\n" + childContent;
        }
        if (node.containsKey(HIGHLIGHT_INDEX)) {
            var highlightIndex = node.getStr(HIGHLIGHT_INDEX);
            var tag = Optional.ofNullable(node.getStr("tagName")).orElse("?");
            var attr = Optional.ofNullable(node.getJSONObject("attributes")).map(this::collectAttribute)
                    .filter(s -> !s.equals(childContent)).orElse(StrUtil.EMPTY);
            var childHighLighted = childList.stream().anyMatch(this::containsHighlighted);
            return childHighLighted ? childContent : String.format("[%s]<%s %s;%s/>", highlightIndex, tag, attr, StrUtil.removeAllSuffix(childContent, "\n"));
        }
        return childContent;
    }

    /**
     * 收集节点的属性信息
     */
    private String collectAttribute(JSONObject node) {
        return node.entrySet().stream()
                .filter(e -> attributeKey.contains(e.getKey()))
                .map(Entry::getValue)
                .map(String::valueOf)
                .reduce((x, y) -> x + ";" + y)
                .orElse(StrUtil.EMPTY);
    }

    public ScrollInfo getScrollInfo() {
        var page = currentPage();
        var scrollY = Convert.toInt(page.evaluate("window.scrollY"));
        var viewportHeight = Convert.toInt(page.evaluate("window.innerHeight"));
        var totalHeight = Convert.toInt(page.evaluate("document.documentElement.scrollHeight"));
        return ScrollInfo.builder()
                .pixelsAbove(scrollY)
                .pixelsBelow(totalHeight - (scrollY + viewportHeight))
                .build();
    }

    @Tool(description = "根据目标，提取相关的内容数据")
    public String extractContent(@ToolParam(description = "要提取的内容描述") String goal) {
        return this.pageContentExtractService.extractContent(originalContent, this.fromAllElementByTree(rootId), goal);
    }

    @Tool(description = "切换到指定的页面标签页")
    public String switchTab(@ToolParam(description = "页面id") int pageId) {
        this.pageIndex = pageId;
        this.reAnalyze();
        this.flagTabChanged = 1;
        return "Action Result:page index change to " + pageId;
    }

    /**
     * 点击指定索引的元素，并处理可能的新页面打开或导航
     */
    @Tool(description = "点击指定索引的元素")
    public String clickElement(@ToolParam(description = "下标") int index) {
        Page page = currentPage();
        String oldUrl = page.url();
        String testId = String.format("zp-%s", index);
        page.getByTestId(testId).click();
        String postfix = waitForNewPage().map(p -> {
            locateOn(p);
            return "new page found after click action:" + p.title();
        }).orElseGet(() -> {
            if (waitNavi(page, oldUrl)) {
                return "navigated to:" + page.title();
            }
            return StrUtil.EMPTY;
        });
        return "Action result: Element clicked!" + postfix;
    }

    /**
     * 导航到指定 URL
     */
    @Tool(description = "导航到指定的url")
    public String goToUrl(@ToolParam(description = "url地址") String url) {
        Page page = browserContext.newPage();
        page.navigate(url);
        waitNavi(page, StrUtil.EMPTY);
        locateOn(page);
        return "Action result: Navigated to " + url;
    }

    /**
     * 在指定索引的输入框中输入文本
     */
    @Tool(description = "输入框中输入文本")
    public String inputText(@ToolParam(description = "下标") int index,@ToolParam(description = "文本内容") String text) {
        Page page = currentPage();
        String testId = String.format("zp-%s", index);
        var locator = page.getByTestId(testId);
        locator.clear();
        locator.fill(text);
        return "Action result: input text success";
    }

    @Tool(description = "等待指定时间")
    public String wait(@ToolParam(description = "等待时间(秒)") int seconds) {
        try {
            Thread.sleep(seconds * 1_000L);
            return "Action result: you have wait " + seconds + " second(s)";
        } catch (InterruptedException e) {
            return "Action result: InterruptedException(" + e.getMessage() + ")";
        }
    }

    /**
     * 用于存储页面滚动信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrollInfo {
        private int pixelsAbove;
        private int pixelsBelow;
    }

    /**
     * 用于存储浏览器标签页的信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrowserTabInfo {
        private int index;
        private String title;
        private String url;
    }
}
