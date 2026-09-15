package kdt.washPrj.auth.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

abstract class AbstractSocialOAuthClient implements SocialOAuthClient {

    private final Logger log = LoggerFactory.getLogger(getClass());
    protected final JsonMapper jsonMapper;
    protected final HttpClient httpClient;

    protected AbstractSocialOAuthClient(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    protected Map<?, ?> sendJson(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn(
                        "{} OAuth {} failed with HTTP {}.",
                        provider(),
                        operation,
                        response.statusCode()
                );
                throw providerFailure("인증 서버가 요청을 처리하지 못했습니다.");
            }
            return jsonMapper.readValue(response.body(), Map.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerFailure("인증 요청이 중단되었습니다.");
        } catch (SocialOAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn(
                    "{} OAuth {} failed: {}",
                    provider(),
                    operation,
                    exception.getMessage()
            );
            throw providerFailure("인증 서버에 연결하지 못했습니다.");
        }
    }

    protected void requireConfigured() {
        if (!isConfigured()) {
            throw new SocialOAuthException(
                    provider().name() + "_LOGIN_NOT_CONFIGURED",
                    provider().displayName() + " 로그인 환경변수가 설정되지 않았습니다.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    protected SocialOAuthException providerFailure(String message) {
        return new SocialOAuthException(
                provider().name() + "_PROVIDER_ERROR",
                provider().displayName() + " " + message,
                HttpStatus.BAD_GATEWAY
        );
    }

    protected static String encode(String value) {
        return URLEncoder.encode(
                value == null ? "" : value,
                StandardCharsets.UTF_8
        );
    }

    protected static String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    protected static boolean booleanValue(Object value) {
        return value instanceof Boolean bool
                ? bool
                : "true".equalsIgnoreCase(text(value));
    }

    protected static String normalizeEmail(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    protected static String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.startsWith("82") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.matches("^01[016789][0-9]{7,8}$") ? digits : null;
    }
}
