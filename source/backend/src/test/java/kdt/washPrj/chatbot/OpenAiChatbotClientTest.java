package kdt.washPrj.chatbot;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiChatbotClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void modelCanSelectToolWithoutReceivingPersonalToolOutput() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "output": [{
                        "type": "function_call",
                        "name": "get_my_balances",
                        "call_id": "call_1",
                        "arguments": "{}"
                      }],
                      "usage": {"input_tokens": 20, "output_tokens": 5, "total_tokens": 25}
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        ChatbotMetrics metrics = new ChatbotMetrics(new SimpleMeterRegistry(), 1, 2);
        OpenAiChatbotClient client = new OpenAiChatbotClient(
                new ObjectMapper(), metrics, true, "test-key", "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/responses",
                3, 100
        );
        ChatbotToolService toolService = mock(ChatbotToolService.class);
        ChatbotUserContext context = new ChatbotUserContext(
                "customer-secret", "ROLE_CUSTOMER", true
        );
        when(toolService.availableToolNames(context))
                .thenReturn(List.of(ChatbotToolService.GET_MY_BALANCES));
        when(toolService.execute(ChatbotToolService.GET_MY_BALANCES, context))
                .thenReturn(new ChatbotToolResult(
                        ChatbotToolService.GET_MY_BALANCES,
                        "private tool output",
                        "현재 잔액 안내"
                ));

        OpenAiChatbotResult result = client.respondWithTools(
                "안내 지침", List.of(), "내 혜택 알려줘", context, toolService
        ).orElseThrow();

        assertEquals("현재 잔액 안내", result.answer());
        assertEquals(List.of(ChatbotToolService.GET_MY_BALANCES), result.toolNames());
        assertEquals(1, requestCount.get());
        assertTrue(requestBody.get().contains("get_my_balances"));
        assertFalse(requestBody.get().contains("customer-secret"));
        assertFalse(requestBody.get().contains("private tool output"));
    }
}
