package kdt.washPrj.notification;

import kdt.washPrj.auth.AuthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LineWebhookServiceTest {

    private static final String CODE = "abcdefghijklmnopqrstuv";
    private static final String USER_ID = "U11111111111111111111111111111111";

    private LineWebhookSignatureVerifier signatureVerifier;
    private LineLinkCodeStore linkCodeStore;
    private AuthMapper authMapper;
    private LineNotificationClient lineClient;
    private LineWebhookService service;

    @BeforeEach
    void setUp() {
        signatureVerifier = mock(LineWebhookSignatureVerifier.class);
        linkCodeStore = mock(LineLinkCodeStore.class);
        authMapper = mock(AuthMapper.class);
        lineClient = mock(LineNotificationClient.class);
        service = new LineWebhookService(
                signatureVerifier,
                linkCodeStore,
                authMapper,
                lineClient,
                JsonMapper.builder().build()
        );
    }

    @Test
    void rejectsWebhookWithInvalidSignatureBeforeParsingBody() {
        when(signatureVerifier.verify("not-json", "bad")).thenReturn(false);

        assertThrows(
                ResponseStatusException.class,
                () -> service.handle("not-json", "bad")
        );

        verifyNoInteractions(linkCodeStore, authMapper, lineClient);
    }

    @Test
    void linksCustomerAndRepliesForValidCommand() {
        String body = messageBody("연결 " + CODE);
        when(signatureVerifier.verify(body, "valid")).thenReturn(true);
        when(linkCodeStore.resolve(CODE)).thenReturn("customer1");
        when(authMapper.linkSocialAccount("LINE", USER_ID, "customer1"))
                .thenReturn("OK");

        service.handle(body, "valid");

        verify(authMapper).linkSocialAccount("LINE", USER_ID, "customer1");
        verify(linkCodeStore).invalidate(CODE);
        verify(lineClient).replyMessage("reply-token", "LINE 알림 연결이 완료되었습니다.");
    }

    @Test
    void expiredCodeNeverLinksAccount() {
        String body = messageBody("연결 " + CODE);
        when(signatureVerifier.verify(body, "valid")).thenReturn(true);
        when(linkCodeStore.resolve(CODE)).thenReturn(null);

        service.handle(body, "valid");

        verify(authMapper, never()).linkSocialAccount(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(lineClient).replyMessage(
                "reply-token",
                "연결 문구가 만료되었거나 이미 사용되었습니다. 웹사이트에서 새로 발급해 주세요."
        );
    }

    private String messageBody(String text) {
        return """
                {"events":[{"type":"message","replyToken":"reply-token","source":{"type":"user","userId":"%s"},"message":{"type":"text","text":"%s"}}]}
                """.formatted(USER_ID, text).trim();
    }
}
