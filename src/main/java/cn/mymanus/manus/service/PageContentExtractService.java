package cn.mymanus.manus.service;

/**
 * 提取页面内容服务
 */
public interface PageContentExtractService {

    /**
     * 提取页面中的内容
     *
     * @param originalContent 原始内容（包含所有内容，冗余信息较多，容易超token限制）
     * @param pageInStatus    当前页面的状态（可见的基础标签及文本）
     * @param goal            提取的目标内容
     * @return 提取到的内容
     */
    String extractContent(String originalContent, String pageInStatus, String goal);
}
