package com.pomodoro.vo.rank;

import lombok.Data;

/**
 * 排行榜条目出参
 */
@Data
public class RankItemVO {

    private int rank;
    private Long userId;
    private String username;
    private String nickname;
    private int level;
    private int points;
    /** 是否当前用户 */
    private Boolean isMe;
}
