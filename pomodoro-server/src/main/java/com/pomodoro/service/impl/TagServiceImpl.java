package com.pomodoro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pomodoro.common.BusinessException;
import com.pomodoro.common.ResultCode;
import com.pomodoro.dto.tag.TagCreateRequest;
import com.pomodoro.dto.tag.TagUpdateRequest;
import com.pomodoro.entity.Tag;
import com.pomodoro.entity.TaskTag;
import com.pomodoro.mapper.TagMapper;
import com.pomodoro.mapper.TaskTagMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.TagService;
import com.pomodoro.vo.tag.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签服务实现
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final TaskTagMapper taskTagMapper;

    @Override
    public TagVO create(TagCreateRequest request) {
        Long userId = UserContext.requireUserId();
        String name = request.getName().trim();

        // 同用户内标签名唯一（应用层兜底，schema 无唯一键）
        Long count = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId).eq(Tag::getName, name));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "标签名已存在");
        }

        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(request.getColor() != null ? request.getColor() : "#1f5fd0");
        tagMapper.insert(tag);
        return TagVO.of(tag);
    }

    @Override
    public List<TagVO> list() {
        Long userId = UserContext.requireUserId();
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId).orderByAsc(Tag::getCreatedAt));
        return tags.stream().map(TagVO::of).collect(Collectors.toList());
    }

    @Override
    public TagVO update(Long id, TagUpdateRequest request) {
        Tag tag = getOwnedTag(id);

        if (request.getName() != null) {
            String name = request.getName().trim();
            Long count = tagMapper.selectCount(
                    new LambdaQueryWrapper<Tag>()
                            .eq(Tag::getUserId, tag.getUserId())
                            .eq(Tag::getName, name)
                            .ne(Tag::getId, id));
            if (count != null && count > 0) {
                throw new BusinessException(ResultCode.CONFLICT, "标签名已存在");
            }
            tag.setName(name);
        }
        if (request.getColor() != null) {
            tag.setColor(request.getColor());
        }

        tagMapper.updateById(tag);
        return TagVO.of(tag);
    }

    @Override
    public void delete(Long id) {
        Tag tag = getOwnedTag(id);
        // 先清关联再删标签
        taskTagMapper.delete(new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTagId, id));
        tagMapper.deleteById(tag.getId());
    }

    /**
     * 取当前用户拥有的标签，不存在或非本人返回 404
     */
    private Tag getOwnedTag(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getUserId().equals(UserContext.requireUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }
}
