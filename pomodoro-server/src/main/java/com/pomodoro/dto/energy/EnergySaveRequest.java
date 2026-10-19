package com.pomodoro.dto.energy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 精力自评请求体
 */
@Data
public class EnergySaveRequest {

    /** 精力自评 1-5 */
    @NotNull(message = "精力自评不能为空")
    @Min(value = 1, message = "精力自评最低为 1")
    @Max(value = 5, message = "精力自评最高为 5")
    private Integer energyLevel;

    /** 心情（可选） */
    @Size(max = 32, message = "心情长度不能超过 32")
    private String mood;

    /** 备注（可选） */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String note;

    /** 记录日期（可选，默认今天；未来日期拒绝） */
    private LocalDate date;
}
