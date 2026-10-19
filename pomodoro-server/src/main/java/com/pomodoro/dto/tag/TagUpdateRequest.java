package com.pomodoro.dto.tag;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新标签请求体（字段可部分更新，null 表示不修改）
 */
@Data
public class TagUpdateRequest {

    @Size(max = 32, message = "标签名长度不能超过 32")
    private String name;

    @Size(max = 16, message = "颜色长度不能超过 16")
    private String color;
}
