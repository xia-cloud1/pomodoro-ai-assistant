package com.pomodoro.service;

import com.pomodoro.dto.tag.TagCreateRequest;
import com.pomodoro.dto.tag.TagUpdateRequest;
import com.pomodoro.vo.tag.TagVO;

import java.util.List;

/**
 * 标签服务
 */
public interface TagService {

    /**
     * 创建标签（同用户内标签名唯一）
     */
    TagVO create(TagCreateRequest request);

    /**
     * 当前用户标签列表
     */
    List<TagVO> list();

    /**
     * 更新标签（部分更新）
     */
    TagVO update(Long id, TagUpdateRequest request);

    /**
     * 删除标签（同时清理任务-标签关联）
     */
    void delete(Long id);
}
