package com.pomodoro.vo.rank;

import lombok.Data;

import java.util.List;

/**
 * 排行榜出参：前 N 名 + 当前用户全局排名
 */
@Data
public class RankVO {

    /** 当前用户全局排名（1-based） */
    private Integer myRank;

    private List<RankItemVO> list;
}
