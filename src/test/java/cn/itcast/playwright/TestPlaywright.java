package cn.itcast.playwright;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.*;

public class TestPlaywright {

    // 测试网站导航功能
    @Test
    public void testLaunch() {
        // 创建Playwright实例并管理资源
        try (Playwright playwright = Playwright.create()) {
            // 获取Chromium浏览器类型
            BrowserType chromium = playwright.chromium();
            // 启动Chromium浏览器，设置非无头模式以便观察
            Browser browser = chromium.launch(new BrowserType.LaunchOptions().setHeadless(false));
            // 新建一个页面
            Page page = browser.newPage();
            // 设置页面操作的默认超时时间为60秒
            page.setDefaultTimeout(60_000);
            // 导航到指定URL
            page.navigate("https://www.baidu.com/");
            // 关闭浏览器
            browser.close();
        }
    }

    @Test
    public void testPageAnnotation() {
        // 获取js文件内容
        String buildDomTree = ResourceUtil.readUtf8Str("js/buildDomTree.js");

        // 创建Playwright实例并管理资源
        try (Playwright playwright = Playwright.create()) {
            // 获取Chromium浏览器类型
            BrowserType chromium = playwright.chromium();
            // 启动Chromium浏览器，设置非无头模式以便观察
            Browser browser = chromium.launch(new BrowserType.LaunchOptions().setHeadless(false));
            // 新建一个页面
            Page page = browser.newPage();
            // 设置页面操作的默认超时时间为60秒
            page.setDefaultTimeout(60_000);
            // 导航到指定URL
            page.navigate("https://www.baidu.com/");

            // 页面执行标注js
            Object evaluate = page.evaluate(buildDomTree);
            JSONObject jsonObject = JSONUtil.parseObj(evaluate);
            // 获取根节点的indexId
            var rootId = jsonObject.getInt("rootId");

            // 将map中所有的节点元素都放到allElement中
            Map<Integer, JSONObject> allElement = new TreeMap<>();
            JSONObject map = jsonObject.getJSONObject("map");
            map.forEach((k, v) -> allElement.put(Convert.toInt(k), (JSONObject) v));

            // 获取页面中所有【有价值】的内容，这部分内容就是可以发给大模型进行分析的
            String body = this.fromAllElementByTree(rootId, allElement);
            System.out.println(body);


            // 关闭浏览器
            browser.close();
        }
    }

    /**
     * 递归生成页面元素树的字符串
     */
    private String fromAllElementByTree(Integer indexId, Map<Integer, JSONObject> allElement) {
        JSONObject node = allElement.get(indexId);
        var childList = childIdxList(node);
        String childContent = childList.stream()
                .map(id -> this.fromAllElementByTree(id, allElement))
                .filter(StrUtil::isNotBlank)
                .reduce((x, y) -> x + "\n" + y)
                .orElse(StrUtil.EMPTY);
        if (!node.getBool("isVisible", false)) {
            return childContent;
        }
        if ("TEXT_NODE".equals(node.getStr("type"))) {
            return node.getStr("text") + "\n" + childContent;
        }
        if (node.containsKey("highlightIndex")) {
            String highlightIndex = node.getStr("highlightIndex");
            String tag = Optional.ofNullable(node.getStr("tagName")).orElse("?");
            String attr = Optional.ofNullable(node.getJSONObject("attributes"))
                    .map(this::collectAttribute)
                    .filter(s -> !s.equals(childContent))
                    .orElse(StrUtil.EMPTY);
            boolean childHighLighted = childList.stream().anyMatch(p -> this.containsHighlighted(allElement, p));
            return childHighLighted ? childContent : String.format("[%s]<%s %s;%s/>", highlightIndex, tag, attr, StrUtil.removeAllSuffix(childContent, "\n"));
        }
        return childContent;
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
     * 收集节点的属性信息
     */
    private String collectAttribute(JSONObject node) {
        Set<String> attributeKey = Set.of("title", "type", "name", "role", "tabindex", "aria-label", "placeholder", "value", "alt", "aria-expanded", "class");
        return node.entrySet().stream()
                .filter(e -> attributeKey.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .map(String::valueOf)
                .reduce((x, y) -> x + ";" + y)
                .orElse(StrUtil.EMPTY);
    }

    private boolean containsHighlighted(Map<Integer, JSONObject> allElement, Integer indexId) {
        JSONObject node = allElement.get(indexId);
        if (node.containsKey("highlightIndex")) {
            return true;
        }

        return childIdxList(node).stream().anyMatch(p -> this.containsHighlighted(allElement, p));
    }
}