package cn.itcast.manus.enums;

import cn.hutool.core.util.EnumUtil;
import lombok.Getter;

/**
 * 智能体类型
 */
@Getter
public enum AgentTypeEnum {

    RE_ACT_PLANNING_AGENT("reActPlanningAgent", "任务规划智能体"),
    BROWSER_AGENT("BrowserAgent", "浏览器Agent可以进行通用浏览器操作，例如通过网站查询到需要的信息或是进行指定的网页操作"),
    TABLE_AGENT("TableAgent", "此Agent专职用于绘制表格，只能基于上下文中已有的数据进行绘制，无法查询额外信息"),
    CHART_AGENT("ChartAgent", "此Agent专职用于绘制统计图，只能基于上下文中已有的数据进行绘制，无法查询额外信息"),
    HTML_DOC_AGENT("HtmlDocAgent", "此Agent用于生成各类网页内容，只能基于上下文中已有的数据进行生成，无法查询额外信息；可作为生成一般内容时的默认Agent"),
    AMAP_AGENT("AMAPAgent", "此Agent包含完整的地图工具集，可用于路线规划、结构化地址转换为经纬度坐标等地理信息操作，返回文字或多媒体链接的结果");

    private final String agentName;
    private final String desc;

    AgentTypeEnum(String agentName, String desc) {
        this.agentName = agentName;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return this.name();
    }


    /**
     * 通过智能体的名称查找枚举
     */
    public static AgentTypeEnum agentNameOf(String agentName) {
        return EnumUtil.getBy(AgentTypeEnum::getAgentName, agentName);
    }
}
