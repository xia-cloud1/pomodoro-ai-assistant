package com.pomodoro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pomodoro.entity.TaskTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 任务-标签关联 Mapper
 */
public interface TaskTagMapper extends BaseMapper<TaskTag> {

    /**
     * 物理删除某用户的全部任务-标签关联（含已软删任务，用于注销账号）
     */
    @Delete("DELETE FROM task_tag WHERE task_id IN (SELECT id FROM task WHERE user_id = #{userId}) " +
            "OR tag_id IN (SELECT id FROM tag WHERE user_id = #{userId})")
    int deleteByUserId(@Param("userId") Long userId);
}
