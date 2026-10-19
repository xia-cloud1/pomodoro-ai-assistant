package com.pomodoro.vo.auth;

import com.pomodoro.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息出参（不含密码等敏感字段）
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Integer level;
    private Integer points;
    private LocalDateTime createdAt;

    /** 从实体转换为脱敏出参 */
    public static UserVO of(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setLevel(user.getLevel());
        vo.setPoints(user.getPoints());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
