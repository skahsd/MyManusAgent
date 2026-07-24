package cn.mymanus.manus.agent;

import cn.hutool.core.util.StrUtil;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public abstract class BaseAgent implements Agent{

    // 标识该agent是否在处理中，true：处理中，false：空闲中
    private volatile boolean solving;

    /**
     * 处理任务
     *
     * @param task 任务描述
     * @return 处理结果
     */
    @Override
    public synchronized String solveTask(String task) {
        try {
            this.solving = true;
            return this.solve(task);
        } catch (Exception e) {
            log.error("error in agent solve", e);
            return StrUtil.format("[{}]:{}", name(), e.getMessage());
        } finally {
            this.solving = false;
        }
    }

    /**
     * 抽象方法，由子类实现
     *
     * @param task 任务描述
     * @return 处理结果
     */
    protected abstract String solve(String task);

}
