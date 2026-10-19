package com.pomodoro.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * DeepSeek 客户端（OpenAI 兼容协议）。
 * 未配置 key 或调用失败时降级返回 null，不抛异常（保证主流程可用）。
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);
    private static final String PLACEHOLDER_KEY = "your-deepseek-api-key";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.base-url}")
    private String baseUrl;
    @Value("${deepseek.api-key}")
    private String apiKey;
    @Value("${deepseek.model}")
    private String model;

    public DeepSeekClient(ObjectMapper objectMapper,
                          RestTemplateBuilder builder,
                          @Value("${deepseek.connect-timeout:10000}") int connectTimeout,
                          @Value("${deepseek.read-timeout:120000}") int readTimeout) {
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .build();
    }

    /**
     * 调用 chat/completions，返回助手文本；未配置或失败返回 null
     */
    /**
     * 是否已配置可用的 API key
     */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && !PLACEHOLDER_KEY.equals(apiKey);
    }

    public String chat(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            log.info("DeepSeek API key 未配置，AI 分析降级为空");
            return null;
        }
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userMessage);
            body.put("temperature", 0.7);
            body.put("max_tokens", 1200);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            return root.path("choices").path(0).path("message").path("content").asText(null);
        } catch (Exception e) {
            log.warn("调用 DeepSeek 失败，AI 分析降级为空：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式调用 chat/completions（stream=true），逐段回调；未配置或失败时静默不回调
     */
    public void streamChat(String systemPrompt, String userMessage, Consumer<String> onChunk) {
        if (!isConfigured()) {
            return;
        }
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userMessage);
            body.put("temperature", 0.7);
            body.put("max_tokens", 1200);
            body.put("stream", true);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

            restTemplate.execute(url, HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getHeaders().setBearerAuth(apiKey);
                        request.getBody().write(payload);
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    String data = line.substring(5).trim();
                                    if ("[DONE]".equals(data)) {
                                        break;
                                    }
                                    String content = extractDelta(data);
                                    if (content != null && !content.isEmpty()) {
                                        onChunk.accept(content);
                                    }
                                }
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.warn("流式调用 DeepSeek 失败：{}", e.getMessage());
        }
    }

    /** 从 SSE data 行提取增量文本 */
    private String extractDelta(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() ? null : content.asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
