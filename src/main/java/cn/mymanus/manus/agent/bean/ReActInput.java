package cn.mymanus.manus.agent.bean;

import cn.hutool.core.annotation.Alias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActInput {

    private List<Map<String, Object>> action;
    @Alias("current_state")
    private Status status;

    @Data
    public static class Status {
        @Alias("evaluation_previous_goal")
        private String evaluationPreviousGoal;
        private String thinking;
        private String memory;
    }

}


