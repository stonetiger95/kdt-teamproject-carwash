package kdt.washPrj.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class LineNotificationClient {

    private final LineMessagingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public LineNotificationClient(
            LineMessagingProperties properties,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(4))
                        .build()
        );
    }

    LineNotificationClient(
            LineMessagingProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isConfigured() {
        return properties.isEnabled()
                && hasText(properties.getChannelAccessToken())
                && hasText(properties.getApiUrl());
    }

    public void sendMessage(String recipientId, String message) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "LINE Messaging API가 활성화되지 않았거나 channel access token이 설정되지 않았습니다."
            );
        }
        if (!hasText(recipientId)
                || !recipientId.matches("U[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("LINE 수신자 ID가 올바르지 않습니다.");
        }
        if (!hasText(message)) {
            throw new IllegalArgumentException("LINE 메시지가 비어 있습니다.");
        }

        send(
                properties.getApiUrl(),
                Map.of(
                    "to", recipientId,
                    "messages", List.of(Map.of(
                            "type", "text",
                            "text", message
                    ))
                ),
                true
        );
    }

    public void replyMessage(String replyToken, String message) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "LINE Messaging API가 활성화되지 않았거나 channel access token이 설정되지 않았습니다."
            );
        }
        if (!hasText(replyToken) || replyToken.length() > 2_048) {
            throw new IllegalArgumentException("LINE reply token이 올바르지 않습니다.");
        }
        if (!hasText(message)) {
            throw new IllegalArgumentException("LINE 메시지가 비어 있습니다.");
        }

        send(
                properties.getReplyApiUrl(),
                Map.of(
                        "replyToken", replyToken,
                        "messages", List.of(Map.of(
                                "type", "text",
                                "text", message
                        ))
                ),
                false
        );
    }

    private void send(String apiUrl, Map<String, ?> payload, boolean retryable) {
        if (!hasText(apiUrl)) {
            throw new IllegalStateException("LINE Messaging API URL이 설정되지 않았습니다.");
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("LINE 메시지 요청을 만들지 못했습니다.", exception);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + properties.getChannelAccessToken())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (retryable) {
            request.header("X-Line-Retry-Key", UUID.randomUUID().toString());
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "LINE 메시지 발송 실패: HTTP " + response.statusCode()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LINE 메시지 발송이 중단되었습니다.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("LINE Messaging API에 연결할 수 없습니다.", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
