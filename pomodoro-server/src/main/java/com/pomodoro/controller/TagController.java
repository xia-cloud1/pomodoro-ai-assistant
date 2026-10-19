package com.pomodoro.controller;

import com.pomodoro.common.Result;
import com.pomodoro.dto.tag.TagCreateRequest;
import com.pomodoro.dto.tag.TagUpdateRequest;
import com.pomodoro.service.TagService;
import com.pomodoro.vo.tag.TagVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口
 */
@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * 创建标签
     */
    @PostMapping
    public Result<TagVO> create(@Valid @RequestBody TagCreateRequest request) {
        return Result.success(tagService.create(request));
    }

    /**
     * 标签列表
     */
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.success(tagService.list());
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public Result<TagVO> update(@PathVariable Long id, @Valid @RequestBody TagUpdateRequest request) {
        return Result.success(tagService.update(id, request));
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success();
    }
}
