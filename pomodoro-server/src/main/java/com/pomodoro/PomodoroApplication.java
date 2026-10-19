package com.pomodoro;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 番茄钟 · AI 效率分析系统 启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.pomodoro.mapper")
public class PomodoroApplication {

    public static void main(String[] args) {
        SpringApplication.run(PomodoroApplication.class, args);
    }
}
