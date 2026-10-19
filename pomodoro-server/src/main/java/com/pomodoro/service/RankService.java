package com.pomodoro.service;

import com.pomodoro.vo.rank.RankVO;

/**
 * 排行榜服务
 */
public interface RankService {

    /**
     * 全局排行榜：按 points 降序、level 降序、id 升序，返回前 N 名 + 当前用户排名
     */
    RankVO rank(int limit);
}
