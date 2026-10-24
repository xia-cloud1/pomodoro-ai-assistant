package com.pomodoro.common;

import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

/**
 * 显式声明 {@code charset=UTF-8} 的 SSE emitter。
 * <p>
 * 为什么需要它：{@link SseEmitter} 自己把响应头写成裸的 {@code text/event-stream}，
 * 且这一步发生在内容协商之后，{@code @GetMapping(produces=...)} 覆盖不了它。于是：
 * 服务端写出的事件帧字节是 UTF-8 正确的，但响应头不声明编码时，按响应头判编码的客户端
 * （Java/OkHttp 缺省的 ISO-8859-1、部分 App 端请求库）会把中文解成乱码。
 * 浏览器 {@code EventSource} / 前端 {@code TextDecoder('utf-8')} 按规范强制 UTF-8，
 * 所以在 H5 上看不出问题，问题只在跨端时暴露。
 * <p>
 * 为什么不用 {@code produces = "text/event-stream;charset=UTF-8"} 修：
 * {@code produces} 参与内容协商，而前端发的是不带参数的 {@code Accept: text/event-stream}，
 * 一旦协商规则收紧（或将来换客户端）就可能直接 406，把整条流式链路打挂——用一个响应头
 * 去冒接口不可用的风险不值得。覆写 {@code extendResponse} 只改响应头，完全不碰协商。
 */
public class Utf8SseEmitter extends SseEmitter {

    /** SSE 规范固定 UTF-8，这里把编码显式写进响应头，让所有客户端都能正确解码 */
    private static final MediaType UTF8_EVENT_STREAM =
            new MediaType("text", "event-stream", StandardCharsets.UTF_8);

    public Utf8SseEmitter(Long timeout) {
        super(timeout);
    }

    @Override
    protected void extendResponse(ServerHttpResponse outputMessage) {
        super.extendResponse(outputMessage);
        outputMessage.getHeaders().setContentType(UTF8_EVENT_STREAM);
    }
}