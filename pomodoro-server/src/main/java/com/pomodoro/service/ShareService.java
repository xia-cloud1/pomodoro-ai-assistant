package com.pomodoro.service;

/**
 * 分享海报服务
 */
public interface ShareService {

    /**
     * 生成分享海报（PNG 字节，Java2D 渲染）
     */
    byte[] poster();
}
