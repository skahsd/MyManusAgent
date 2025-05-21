package cn.itcast.manus.constants;

/**
 * 常量
 */
public interface Constant {

    String TASK = "task";
    String AGENT_DATA = "agentData";
    String MAX_ACTIONS = "max_actions";

    /**
     * 提示词常量
     */
    interface Prompts {
        String AMAP_SYSTEM = "promptAmapSystem";
        String BROWSER_SYSTEM = "promptBrowserSystem";
        String BROWSER_USER_TASK = "promptBrowserUserTask";
        String CHART = "promptChart";
        String EXTRA_PAGE_CONTENT = "promptExtraPageContent";
        String HTML_DOC = "promptHtmlDoc";
        String PAGE_STATUS = "promptPageStatus";
        String PLANNING_STATUS = "promptPlanningStatus";
        String PLANNING_SYSTEM = "promptPlanningSystem";
        String PLANNING_TASK_MERGING = "promptPlanningTaskMerging";
        String PLANNING_USER_TASK = "promptPlanningUserTask";
        String TABLE = "promptTable";
        String VISION_EXTRACT = "promptVisionExtract";
    }
}
