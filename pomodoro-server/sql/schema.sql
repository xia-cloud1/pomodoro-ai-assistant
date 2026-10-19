-- =============================================================
-- 番茄钟 · AI 效率分析系统 数据库初始化脚本
-- MySQL 8.0 / utf8mb4
-- 说明：本脚本由 docker-compose 挂载到 /docker-entrypoint-initdb.d/
--       数据卷首次为空时自动执行，之后修改需 docker compose down -v 重建
-- =============================================================

CREATE DATABASE IF NOT EXISTS pomodoro DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pomodoro;

-- ---------------------------------------------------------------
-- 1. 用户表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL                COMMENT '用户名（唯一）',
    password    VARCHAR(128) NOT NULL                COMMENT 'BCrypt 密码哈希',
    nickname    VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
    email       VARCHAR(128) DEFAULT NULL            COMMENT '邮箱',
    avatar      VARCHAR(255) DEFAULT NULL            COMMENT '头像地址',
    level       INT          NOT NULL DEFAULT 1      COMMENT '等级',
    points      INT          NOT NULL DEFAULT 0      COMMENT '积分',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ---------------------------------------------------------------
-- 2. 任务表（四象限 / 子任务 / 重复任务 / 习惯打卡）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id              BIGINT       NOT NULL                COMMENT '所属用户',
    parent_id            BIGINT       DEFAULT NULL            COMMENT '父任务 id（子任务指向父任务）',
    title                VARCHAR(255) NOT NULL                COMMENT '任务标题',
    description          TEXT                                 COMMENT '任务描述',
    quadrant             TINYINT      NOT NULL DEFAULT 2      COMMENT '四象限：1紧急重要 2重要不紧急 3紧急不重要 4不紧急不重要',
    status               TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0未开始 1进行中 2已完成 3已归档',
    estimated_pomodoros  INT          NOT NULL DEFAULT 1      COMMENT '预估番茄数',
    actual_pomodoros     INT          NOT NULL DEFAULT 0      COMMENT '实际番茄数',
    due_date             DATE         DEFAULT NULL            COMMENT '截止日期',
    repeat_type          VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT '重复类型：NONE/DAILY/WEEKLY',
    streak_count         INT          NOT NULL DEFAULT 0      COMMENT '连续打卡天数',
    last_checkin_date    DATE         DEFAULT NULL            COMMENT '最近一次打卡日期',
    is_deleted           TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0否 1是',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_task_user (user_id),
    KEY idx_task_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '任务表';

-- ---------------------------------------------------------------
-- 3. 标签表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL                COMMENT '所属用户',
    name       VARCHAR(32) NOT NULL                COMMENT '标签名',
    color      VARCHAR(16) DEFAULT '#1f5fd0'       COMMENT '标签颜色',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_tag_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标签表';

-- ---------------------------------------------------------------
-- 4. 任务-标签关联表（多对多）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_tag (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务 id',
    tag_id  BIGINT NOT NULL COMMENT '标签 id',
    PRIMARY KEY (id),
    KEY idx_task_tag_task (task_id),
    KEY idx_task_tag_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '任务-标签关联表';

-- ---------------------------------------------------------------
-- 5. 番茄记录表（计时状态机 + 服务端对账防作弊）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pomodoro_record (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT       NOT NULL                COMMENT '所属用户',
    task_id          BIGINT       DEFAULT NULL            COMMENT '关联任务',
    start_time       DATETIME     NOT NULL                COMMENT '开始时间（客户端上报）',
    end_time         DATETIME     DEFAULT NULL            COMMENT '结束时间（客户端上报）',
    duration         INT          NOT NULL DEFAULT 0      COMMENT '实际专注时长（秒，服务端按 start/end 对账）',
    status           VARCHAR(16)  NOT NULL DEFAULT 'COMPLETED' COMMENT '状态：RUNNING 进行中 / COMPLETED 完成 / INTERRUPTED 中断',
    interrupt_reason VARCHAR(255) DEFAULT NULL            COMMENT '中断原因',
    white_noise      VARCHAR(32)  DEFAULT NULL            COMMENT '使用的白噪音类型',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 生成列：只有 RUNNING 记录取 user_id，其余为 NULL。
    -- MySQL 唯一索引允许多个 NULL，因此该约束等价于「每个用户最多一条进行中记录」，
    -- 用来兜住「先查再插」在并发下的竞态窗口（见 PomodoroServiceImpl.start）。
    running_user_id  BIGINT       GENERATED ALWAYS AS (IF(status = 'RUNNING', user_id, NULL)) STORED COMMENT '仅进行中记录有值，用于并发唯一约束',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_running_user (running_user_id),
    KEY idx_pr_user (user_id),
    KEY idx_pr_user_start (user_id, start_time),
    KEY idx_pr_user_task (user_id, task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '番茄记录表';

-- 已存在数据库的增量迁移（新库由上面的建表语句直接包含，无需执行）：
-- ALTER TABLE pomodoro_record
--   ADD COLUMN running_user_id BIGINT GENERATED ALWAYS AS (IF(status = 'RUNNING', user_id, NULL)) STORED
--       COMMENT '仅进行中记录有值，用于并发唯一约束',
--   ADD UNIQUE KEY uk_pr_running_user (running_user_id);

-- ---------------------------------------------------------------
-- 6. 日统计聚合表（定时任务写入，供报表/热力图/效率得分查询）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS daily_stat (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id               BIGINT       NOT NULL                COMMENT '所属用户',
    stat_date             DATE         NOT NULL                COMMENT '统计日期',
    total_pomodoros       INT          NOT NULL DEFAULT 0      COMMENT '当日完成番茄数',
    total_minutes         INT          NOT NULL DEFAULT 0      COMMENT '当日专注总分钟数',
    completed_count       INT          NOT NULL DEFAULT 0      COMMENT '完成次数',
    interrupted_count     INT          NOT NULL DEFAULT 0      COMMENT '中断次数',
    max_focus_minutes     INT          NOT NULL DEFAULT 0      COMMENT '最长单次专注（分钟）',
    avg_focus_minutes     INT          NOT NULL DEFAULT 0      COMMENT '平均单次专注（分钟）',
    focus_score           INT          NOT NULL DEFAULT 0      COMMENT '效率得分（0-100）',
    category_distribution TEXT                                 COMMENT '分类分布（JSON 文本）',
    hour_distribution     TEXT                                 COMMENT '时段分布（JSON 文本）',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stat_user_date (user_id, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '日统计聚合表';

-- ---------------------------------------------------------------
-- 7. 周目标表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS goal (
    id                BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT NOT NULL                COMMENT '所属用户',
    week_start        DATE   NOT NULL                COMMENT '周起始日期（周一）',
    target_pomodoros  INT    NOT NULL DEFAULT 0      COMMENT '目标番茄数',
    achieved_pomodoros INT   NOT NULL DEFAULT 0      COMMENT '已达番茄数',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_goal_user_week (user_id, week_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周目标表';

-- ---------------------------------------------------------------
-- 8. 成就定义表（徽章，静态配置）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS achievement (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code            VARCHAR(64)  NOT NULL                COMMENT '成就编码（唯一）',
    name            VARCHAR(64)  NOT NULL                COMMENT '成就名称',
    description     VARCHAR(255) DEFAULT NULL            COMMENT '成就描述',
    icon            VARCHAR(255) DEFAULT NULL            COMMENT '图标（emoji 或图片地址）',
    condition_type  VARCHAR(32)  NOT NULL                COMMENT '条件类型：TOTAL_POMODOROS / STREAK_DAYS',
    condition_value INT          NOT NULL DEFAULT 0      COMMENT '条件阈值',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_achievement_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成就定义表';

-- ---------------------------------------------------------------
-- 9. 用户成就表（用户已解锁的徽章）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_achievement (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT   NOT NULL                COMMENT '所属用户',
    achievement_id BIGINT   NOT NULL                COMMENT '成就 id',
    unlocked_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ua_user_ach (user_id, achievement_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户成就表';

-- ---------------------------------------------------------------
-- 10. 打卡表（连续打卡 / 补签）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS check_in (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT   NOT NULL                COMMENT '所属用户',
    check_date DATE     NOT NULL                COMMENT '打卡日期',
    is_makeup  TINYINT  NOT NULL DEFAULT 0      COMMENT '是否补签：0否 1是',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkin_user_date (user_id, check_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '打卡表';

-- ---------------------------------------------------------------
-- 11. 精力自评表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS energy_log (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT      NOT NULL                COMMENT '所属用户',
    log_date     DATE        NOT NULL                COMMENT '记录日期',
    energy_level TINYINT     NOT NULL DEFAULT 3      COMMENT '精力自评 1-5',
    mood         VARCHAR(32) DEFAULT NULL            COMMENT '心情',
    note         VARCHAR(255) DEFAULT NULL           COMMENT '备注',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_user_date (user_id, log_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '精力自评表';

-- ---------------------------------------------------------------
-- 12. 积分流水表（积分变动记录）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS points_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL                COMMENT '所属用户',
    points_change INT          NOT NULL                COMMENT '积分变动（正增负减）',
    points_type   VARCHAR(32)  NOT NULL                COMMENT '类型：POMODORO/CHECKIN/ACHIEVEMENT',
    ref_id        BIGINT       DEFAULT NULL            COMMENT '关联记录 id（番茄/打卡/成就）',
    note          VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_points_log_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分流水表';

-- ---------------------------------------------------------------
-- 种子数据：成就定义（静态配置）
-- ---------------------------------------------------------------
INSERT INTO achievement (code, name, description, icon, condition_type, condition_value) VALUES
    ('first_pomodoro', '初尝专注', '完成第一个番茄', '🍅', 'TOTAL_POMODOROS', 1),
    ('pomodoro_10',    '渐入佳境', '累计完成 10 个番茄', '🔥', 'TOTAL_POMODOROS', 10),
    ('pomodoro_50',    '专注达人', '累计完成 50 个番茄', '⚡', 'TOTAL_POMODOROS', 50),
    ('pomodoro_100',   '百炼成钢', '累计完成 100 个番茄', '🏆', 'TOTAL_POMODOROS', 100),
    ('streak_3',       '连续三天', '连续打卡 3 天', '🌟', 'STREAK_DAYS', 3),
    ('streak_7',       '一周坚持', '连续打卡 7 天', '💎', 'STREAK_DAYS', 7);
