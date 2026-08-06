package com.xtu.homework.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * DeepSeek 大模型客户端（AI 出题）
 * 配置：ai.api-key / ai.model（环境变量 DEEPSEEK_API_KEY / DEEPSEEK_MODEL 注入，不进 git）
 */
@Service
public class DeepSeekClient {

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:deepseek-v4-flash}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(ObjectMapper objectMapper) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        // 代理探测式使用（本机 Clash 规则模式）：
        // - HTTPS_PROXY 环境变量存在 且 代理端口可连 → 走代理（Clash 对国内域名走 DIRECT，无实质影响）
        // - 代理不可用（Clash 退出）→ 自动直连，不影响国内直连 DeepSeek 的场景
        String proxyEnv = System.getenv("HTTPS_PROXY");
        if (proxyEnv != null && !proxyEnv.isBlank() && !"direct".equalsIgnoreCase(proxyEnv)) {
            try {
                URI pu = URI.create(proxyEnv.contains("://") ? proxyEnv : "http://" + proxyEnv);
                int port = pu.getPort() > 0 ? pu.getPort() : 80;
                if (pu.getHost() != null && isPortOpen(pu.getHost(), port)) {
                    builder.proxy(java.net.ProxySelector.of(new java.net.InetSocketAddress(pu.getHost(), port)));
                }
                // 端口不可达则保持直连
            } catch (Exception ignored) {
                // 代理配置解析失败则直连
            }
        }
        this.httpClient = builder.build();
        this.objectMapper = objectMapper;
    }

    /** 探测代理端口是否可达（1.5s 超时），避免 Clash 未启动时连接失败 */
    private boolean isPortOpen(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通用对话调用（OpenAI 兼容接口），返回模型回复文本
     */
    public String chat(String systemPrompt, String userQuery, double temperature) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("AI 服务未配置（缺少 ai.api-key 环境变量），请联系管理员配置 DEEPSEEK_API_KEY");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);

            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userQuery);
            body.putObject("response_format").put("type", "json_object");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                    return cleanJsonOutput(root.get("choices").get(0).get("message").get("content").asText());
                }
                throw new RuntimeException("AI 响应格式异常: " + response.body());
            }
            throw new RuntimeException("AI 服务调用失败(HTTP " + response.statusCode() + "): "
                    + (response.body() != null && response.body().length() > 300 ? response.body().substring(0, 300) : response.body()));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI 服务调用异常: " + e.getMessage(), e);
        }
    }

    /** 清理模型输出的 markdown 代码块包裹 */
    private String cleanJsonOutput(String content) {
        content = content.trim();
        if (content.startsWith("```json")) content = content.substring(7);
        else if (content.startsWith("```")) content = content.substring(3);
        if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
        return content.trim();
    }
}
