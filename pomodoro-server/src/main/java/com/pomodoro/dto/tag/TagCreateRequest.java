package com.pomodoro.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建标签请求体
 */
@Data
public class TagCreateRequest {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 32, message = "标签名长度不能超过 32")
    private String name;

    /** 标签颜色，缺省 #1f5fd0 */
    @Size(max = 16, message = "颜色长度不能超过 16")
    private String color;
}
