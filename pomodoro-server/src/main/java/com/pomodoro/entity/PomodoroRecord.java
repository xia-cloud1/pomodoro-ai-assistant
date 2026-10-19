package com.pomodoro.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 番茄记录实体（pomodoro_record）：计时状态机 + 服务端对账
 */
@Data
@TableName("pomodoro_record")
public class PomodoroRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long taskId;

    /** 开始时间（客户端上报） */
    private LocalDateTime startTime;

    /** 结束时间（客户端上报） */
    private LocalDateTime endTime;

    /** 实际专注时长（秒，服务端按 start/end 对账） */
    private Integer duration;

    /** 状态：COMPLETED 完成 / INTERRUPTED 中断 */
    private String status;

    private String interruptReason;

    /** 使用的白噪音类型 */
    private String whiteNoise;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
