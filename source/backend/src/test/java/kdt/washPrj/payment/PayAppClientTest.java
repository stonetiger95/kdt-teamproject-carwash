package kdt.washPrj.payment;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayAppClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void postFormEncodesRequestAndParsesProviderResponse() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                "state=1&payurl=https%3A%2F%2Fpay.example%2F33&message=ok+done"
        );
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        PayAppClient client = new PayAppClient("https://payapp.invalid");
        Field field = PayAppClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(client, httpClient);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "payrequest");
        form.put("goodname", "Basic wash");

        Map<String, String> actual = client.postForm(form);

        assertEquals("1", actual.get("state"));
        assertEquals("https://pay.example/33", actual.get("payurl"));
        assertEquals("ok done", actual.get("message"));
    }
}
