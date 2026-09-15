package kdt.washPrj.predictiveMaintenance;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class TelegramNotificationClient {

    private final TelegramProperties properties;
    private final HttpClient httpClient;

    public TelegramNotificationClient(TelegramProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newHttpClient();
    }

    public boolean isConfigured() {
        return properties.isEnabled()
                && hasText(properties.getBotToken())
                && hasText(properties.getChatId());
    }

    public String getRecipient() {
        return hasText(properties.getChatId())
                ? properties.getChatId()
                : "UNCONFIGURED";
    }

    public void sendMessage(String message) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "텔레그램이 활성화되지 않았거나 봇 토큰/chat ID가 설정되지 않았습니다."
            );
        }

        String body = "chat_id=" + encode(properties.getChatId())
                + "&text=" + encode(message);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot"
                        + properties.getBotToken() + "/sendMessage"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "텔레그램 메시지 발송 실패: HTTP " + response.statusCode()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("텔레그램 메시지 발송이 중단되었습니다.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("텔레그램 서버에 연결할 수 없습니다.", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
