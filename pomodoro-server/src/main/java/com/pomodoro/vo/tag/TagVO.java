package com.pomodoro.vo.tag;

import com.pomodoro.entity.Tag;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签出参
 */
@Data
public class TagVO {

    private Long id;
    private String name;
    private String color;
    private LocalDateTime createdAt;

    /** 从实体转换为出参 */
    public static TagVO of(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        vo.setCreatedAt(tag.getCreatedAt());
        return vo;
    }
}
