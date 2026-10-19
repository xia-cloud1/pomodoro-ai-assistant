package com.pomodoro.service.impl;

import com.pomodoro.entity.User;
import com.pomodoro.mapper.UserMapper;
import com.pomodoro.security.UserContext;
import com.pomodoro.service.RankService;
import com.pomodoro.vo.rank.RankItemVO;
import com.pomodoro.vo.rank.RankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜服务实现：读 user 表全量排序（读时实时算）
 */
@Service
@RequiredArgsConstructor
public class RankServiceImpl implements RankService {

    private static final int MAX_LIMIT = 100;

    private final UserMapper userMapper;

    @Override
    public RankVO rank(int limit) {
        Long currentUserId = UserContext.requireUserId();
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<User> users = userMapper.selectList(null);
        users.sort((a, b) -> {
            int pa = points(a), pb = points(b);
            if (pa != pb) {
                return pb - pa;
            }
            int la = level(a), lb = level(b);
            if (la != lb) {
                return lb - la;
            }
            return Long.compare(a.getId(), b.getId());
        });

        RankVO vo = new RankVO();
        List<RankItemVO> list = new ArrayList<>();
        for (int i = 0; i < users.size() && i < capped; i++) {
            User u = users.get(i);
            list.add(toItem(u, i + 1, u.getId().equals(currentUserId)));
        }
        vo.setList(list);

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId().equals(currentUserId)) {
                vo.setMyRank(i + 1);
                break;
            }
        }
        return vo;
    }

    private RankItemVO toItem(User u, int rank, boolean isMe) {
        RankItemVO item = new RankItemVO();
        item.setRank(rank);
        item.setUserId(u.getId());
        item.setUsername(u.getUsername());
        item.setNickname(u.getNickname());
        item.setLevel(level(u));
        item.setPoints(points(u));
        item.setIsMe(isMe);
        return item;
    }

    private int points(User u) {
        return u.getPoints() == null ? 0 : u.getPoints();
    }

    private int level(User u) {
        return u.getLevel() == null ? 0 : u.getLevel();
    }
}
