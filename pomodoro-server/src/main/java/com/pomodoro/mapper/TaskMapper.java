package com.pomodoro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pomodoro.entity.Task;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 任务 Mapper
 */
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 物理删除某用户全部任务（绕过 @TableLogic 软删，用于注销账号）
     */
    @Delete("DELETE FROM task WHERE user_id = #{userId}")
    int physicallyDeleteByUserId(@Param("userId") Long userId);
}
