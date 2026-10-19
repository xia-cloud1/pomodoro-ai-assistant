package com.pomodoro.controller;

import com.pomodoro.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分享接口
 */
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * 生成分享海报（PNG）
     */
    @GetMapping("/poster")
    public ResponseEntity<byte[]> poster() {
        byte[] png = shareService.poster();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDisposition(ContentDisposition.inline().filename("focus_poster.png").build());
        return ResponseEntity.ok().headers(headers).body(png);
    }
}
