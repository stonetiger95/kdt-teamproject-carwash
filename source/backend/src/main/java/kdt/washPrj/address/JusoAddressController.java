package kdt.washPrj.address;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/address/juso")
public class JusoAddressController {

    static final String ADDRESS_SELECTED_MESSAGE =
            "STRAFFIC_JUSO_ADDRESS_SELECTED";
    static final String ADDRESS_ERROR_MESSAGE =
            "STRAFFIC_JUSO_ADDRESS_ERROR";

    private static final String CALLBACK_PATH =
            "/api/public/address/juso/callback";

    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final String approvalKey;
    private final String popupUrl;
    private final String callbackBaseUrl;
    private final String frontendOrigin;

    public JusoAddressController(
            JsonMapper jsonMapper,
            @Value("${juso.popup.enabled:false}") boolean enabled,
            @Value("${juso.popup.approval-key:}") String approvalKey,
            @Value("${juso.popup.api-url:https://business.juso.go.kr/addrlink/addrLinkUrl.do}")
            String popupUrl,
            @Value("${juso.popup.callback-base-url:http://localhost}")
            String callbackBaseUrl,
            @Value("${app.frontend-base-url:http://localhost:5173}")
            String frontendBaseUrl
    ) {
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.approvalKey = approvalKey == null
                ? ""
                : approvalKey.trim();
        this.popupUrl = popupUrl;
        this.callbackBaseUrl = stripTrailingSlash(callbackBaseUrl);
        this.frontendOrigin = extractOrigin(frontendBaseUrl);
    }

    @GetMapping(value = "/popup", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> openPopup() {
        if (!enabled || approvalKey.isBlank()) {
            return htmlResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    errorPage("행정안전부 주소 검색 API가 설정되지 않았습니다."),
                    "default-src 'none'; style-src 'unsafe-inline';"
            );
        }

        String returnUrl = UriComponentsBuilder
                .fromUriString(callbackBaseUrl)
                .path(CALLBACK_PATH)
                .build()
                .toUriString();

        String body = """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>도로명주소 검색</title>
                </head>
                <body>
                    <p>행정안전부 도로명주소 검색창을 연결하고 있습니다.</p>
                    <form id="juso-popup-form" method="post" action="%s">
                        <input type="hidden" name="confmKey" value="%s">
                        <input type="hidden" name="returnUrl" value="%s">
                        <input type="hidden" name="resultType" value="4">
                        <input type="hidden" name="useDetailAddr" value="N">
                        <noscript>
                            <button type="submit">주소 검색 계속하기</button>
                        </noscript>
                    </form>
                    <script>
                        document.getElementById("juso-popup-form").submit();
                    </script>
                </body>
                </html>
                """.formatted(
                escapeHtmlAttribute(popupUrl),
                escapeHtmlAttribute(approvalKey),
                escapeHtmlAttribute(returnUrl)
        );

        return htmlResponse(
                HttpStatus.OK,
                body,
                "default-src 'none'; "
                        + "script-src 'unsafe-inline'; "
                        + "style-src 'unsafe-inline'; "
                        + "form-action https://business.juso.go.kr; "
                        + "base-uri 'none'; frame-ancestors 'none';"
        );
    }

    @PostMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleCallback(
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        String roadAddress = firstNonBlank(
                parameters.getFirst("roadAddrPart1"),
                parameters.getFirst("roadFullAddr")
        );

        if (roadAddress == null) {
            return htmlResponse(
                    HttpStatus.BAD_REQUEST,
                    callbackPage(
                            ADDRESS_ERROR_MESSAGE,
                            Map.of("message", "선택한 주소 정보를 확인할 수 없습니다.")
                    ),
                    callbackContentSecurityPolicy()
            );
        }

        Map<String, String> address = new LinkedHashMap<>();
        address.put("roadFullAddr", value(parameters, "roadFullAddr"));
        address.put("roadAddrPart1", roadAddress);
        address.put("roadAddrPart2", value(parameters, "roadAddrPart2"));
        address.put("addrDetail", value(parameters, "addrDetail"));
        address.put("jibunAddr", value(parameters, "jibunAddr"));
        address.put("zipNo", value(parameters, "zipNo"));
        address.put("admCd", value(parameters, "admCd"));
        address.put("rnMgtSn", value(parameters, "rnMgtSn"));
        address.put("bdMgtSn", value(parameters, "bdMgtSn"));

        return htmlResponse(
                HttpStatus.OK,
                callbackPage(ADDRESS_SELECTED_MESSAGE, address),
                callbackContentSecurityPolicy()
        );
    }

    private String callbackPage(
            String messageType,
            Map<String, String> payload
    ) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", messageType);
        message.put("payload", payload);

        String messageJson = safeScriptJson(
                jsonMapper.writeValueAsString(message)
        );
        String targetOriginJson = safeScriptJson(
                jsonMapper.writeValueAsString(frontendOrigin)
        );

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>주소 선택 완료</title>
                </head>
                <body>
                    <p id="result-message">선택한 주소를 Straffic에 전달하고 있습니다.</p>
                    <script>
                        const message = %s;
                        const targetOrigin = %s;
                        if (window.opener && !window.opener.closed) {
                            window.opener.postMessage(message, targetOrigin);
                            window.close();
                        } else {
                            document.getElementById("result-message").textContent =
                                "원래 Straffic 창을 찾을 수 없습니다. 이 창을 닫고 다시 시도해주세요.";
                        }
                    </script>
                </body>
                </html>
                """.formatted(messageJson, targetOriginJson);
    }

    private ResponseEntity<String> htmlResponse(
            HttpStatus status,
            String body,
            String contentSecurityPolicy
    ) {
        return ResponseEntity
                .status(status)
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", contentSecurityPolicy)
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private String errorPage(String message) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>주소 검색 설정 오류</title>
                </head>
                <body>
                    <p>%s</p>
                    <p>창을 닫고 관리자에게 문의해주세요.</p>
                </body>
                </html>
                """.formatted(escapeHtmlText(message));
    }

    private static String value(
            MultiValueMap<String, String> parameters,
            String name
    ) {
        String value = parameters.getFirst(name);
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String callbackContentSecurityPolicy() {
        return "default-src 'none'; "
                + "script-src 'unsafe-inline'; "
                + "style-src 'unsafe-inline'; "
                + "base-uri 'none'; frame-ancestors 'none';";
    }

    private static String extractOrigin(String baseUrl) {
        URI uri = URI.create(stripTrailingSlash(baseUrl));
        if (uri.getScheme() == null || uri.getRawAuthority() == null) {
            throw new IllegalArgumentException(
                    "FRONTEND_BASE_URL must include http:// or https://"
            );
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceFirst("/+$", "");
    }

    private static String safeScriptJson(String json) {
        return json
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
    }

    private static String escapeHtmlAttribute(String value) {
        return escapeHtmlText(value).replace("\"", "&quot;");
    }

    private static String escapeHtmlText(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
