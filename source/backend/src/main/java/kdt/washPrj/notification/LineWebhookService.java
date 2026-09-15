package kdt.washPrj.notification;

import kdt.washPrj.auth.AuthMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LineWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookService.class);
    private static final Pattern LINK_COMMAND = Pattern.compile(
            "^\\s*연결\\s+([A-Za-z0-9_-]{20,64})\\s*$"
    );
    private static final Pattern LINE_USER_ID = Pattern.compile("U[0-9a-fA-F]{32}");

    private final LineWebhookSignatureVerifier signatureVerifier;
    private final LineLinkCodeStore linkCodeStore;
    private final AuthMapper authMapper;
    private final LineNotificationClient lineClient;
    private final ObjectMapper objectMapper;

    public LineWebhookService(
            LineWebhookSignatureVerifier signatureVerifier,
            LineLinkCodeStore linkCodeStore,
            AuthMapper authMapper,
            LineNotificationClient lineClient,
            ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.linkCodeStore = linkCodeStore;
        this.authMapper = authMapper;
        this.lineClient = lineClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(String rawBody, String signature) {
        if (!signatureVerifier.verify(rawBody, signature)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "LINE webhook signature가 올바르지 않습니다."
            );
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(rawBody);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "LINE webhook 요청 본문이 올바르지 않습니다."
            );
        }

        JsonNode events = body.path("events");
        if (!events.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "LINE webhook events가 올바르지 않습니다."
            );
        }

        for (JsonNode event : events) {
            handleEvent(event);
        }
    }

    private void handleEvent(JsonNode event) {
        String eventType = event.path("type").asText("");
        String replyToken = event.path("replyToken").asText("");

        if ("follow".equals(eventType)) {
            replySafely(
                    replyToken,
                    "웹사이트 마이페이지에서 LINE 알림 연결을 누른 뒤 연결 문구를 보내주세요."
            );
            return;
        }
        if (!"message".equals(eventType)
                || !"text".equals(event.path("message").path("type").asText(""))) {
            return;
        }

        String userId = event.path("source").path("userId").asText("");
        if (!LINE_USER_ID.matcher(userId).matches()) {
            replySafely(replyToken, "1:1 채팅에서 LINE 알림 연결을 진행해 주세요.");
            return;
        }

        String text = event.path("message").path("text").asText("");
        Matcher matcher = LINK_COMMAND.matcher(text);
        if (!matcher.matches()) {
            replySafely(
                    replyToken,
                    "연결 문구가 필요합니다. 웹사이트 마이페이지에서 LINE 알림 연결을 눌러주세요."
            );
            return;
        }

        String code = matcher.group(1);
        String loginId = linkCodeStore.resolve(code);
        if (loginId == null) {
            replySafely(
                    replyToken,
                    "연결 문구가 만료되었거나 이미 사용되었습니다. 웹사이트에서 새로 발급해 주세요."
            );
            return;
        }

        String result = authMapper.linkSocialAccount("LINE", userId, loginId);
        switch (result == null ? "" : result) {
            case "OK" -> {
                linkCodeStore.invalidate(code);
                replySafely(replyToken, "LINE 알림 연결이 완료되었습니다.");
            }
            case "PROVIDER_ALREADY_LINKED" -> {
                linkCodeStore.invalidate(code);
                replySafely(replyToken, "이 LINE 계정은 다른 서비스 계정에 연결되어 있습니다.");
            }
            case "LOGIN_ALREADY_LINKED" -> {
                linkCodeStore.invalidate(code);
                replySafely(replyToken, "현재 서비스 계정에는 이미 다른 LINE 계정이 연결되어 있습니다.");
            }
            case "ACCOUNT_NOT_FOUND" -> {
                linkCodeStore.invalidate(code);
                replySafely(replyToken, "고객 또는 업주 계정만 LINE 알림을 연결할 수 있습니다.");
            }
            default -> throw new IllegalStateException("LINE account link failed.");
        }
    }

    private void replySafely(String replyToken, String message) {
        if (replyToken == null || replyToken.isBlank()) {
            return;
        }
        try {
            lineClient.replyMessage(replyToken, message);
        } catch (RuntimeException exception) {
            log.warn("Could not send LINE webhook reply: {}", exception.getMessage());
        }
    }
}
