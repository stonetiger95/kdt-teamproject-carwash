package kdt.washPrj.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PayAppClient {

    private final HttpClient httpClient;
    private final String apiUrl;

    public PayAppClient(@Value("${payapp.api-url}") String apiUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.apiUrl = apiUrl;
    }

    public Map<String, String> postForm(Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        toFormBody(form),
                        StandardCharsets.UTF_8
                ))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "PayApp server returned an error."
                );
            }

            return parseQuery(response.body());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayApp request failed.",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayApp request was interrupted.",
                    e
            );
        }
    }

    private String toFormBody(Map<String, String> form) {
        StringBuilder body = new StringBuilder();

        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append("&");
            }

            body.append(urlEncode(entry.getKey()));
            body.append("=");
            body.append(urlEncode(entry.getValue()));
        }

        return body.toString();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();

        if (query == null || query.isBlank()) {
            return result;
        }

        for (String pair : query.split("&")) {
            int separatorIndex = pair.indexOf("=");
            String key = separatorIndex < 0
                    ? pair
                    : pair.substring(0, separatorIndex);
            String value = separatorIndex < 0
                    ? ""
                    : pair.substring(separatorIndex + 1);
            result.put(urlDecode(key), urlDecode(value));
        }

        return result;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(
                value == null ? "" : value,
                StandardCharsets.UTF_8
        );
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(
                value == null ? "" : value,
                StandardCharsets.UTF_8
        );
    }
}
