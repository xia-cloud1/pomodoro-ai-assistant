package com.pomodoro.dto.tag;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 设置任务标签请求体（整体替换；传空数组 = 清空标签）
 */
@Data
public class TaskTagBindRequest {

    @NotNull(message = "标签列表不能为空")
    private List<Long> tagIds;
}
