package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotHistoryMessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class OpenAiChatbotClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatbotClient.class);

    private final ObjectMapper objectMapper;
    private final ChatbotMetrics metrics;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final Duration timeout;
    private final int maxOutputTokens;

    public OpenAiChatbotClient(
            ObjectMapper objectMapper,
            ChatbotMetrics metrics,
            @Value("${chatbot.openai.enabled:true}") boolean enabled,
            @Value("${chatbot.openai.api-key:}") String apiKey,
            @Value("${chatbot.openai.model:gpt-5.4-mini}") String model,
            @Value("${chatbot.openai.api-url:https://api.openai.com/v1/responses}") String apiUrl,
            @Value("${chatbot.openai.timeout-seconds:15}") int timeoutSeconds,
            @Value("${chatbot.openai.max-output-tokens:350}") int maxOutputTokens
    ) {
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .build();
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.apiUrl = apiUrl;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.maxOutputTokens = Math.max(100, maxOutputTokens);
    }

    public boolean isConfigured() {
        return enabled && !apiKey.isBlank();
    }

    public Optional<String> respond(
            String instructions,
            List<ChatbotHistoryMessageDTO> history,
            String message
    ) {
        return respondWithTools(
                instructions,
                history,
                message,
                ChatbotUserContext.anonymous(),
                null
        ).map(OpenAiChatbotResult::answer);
    }

    public Optional<OpenAiChatbotResult> respondWithTools(
            String instructions,
            List<ChatbotHistoryMessageDTO> history,
            String message,
            ChatbotUserContext context,
            ChatbotToolService toolService
    ) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        ObjectNode payload = basePayload(instructions);
        ArrayNode input = payload.putArray("input");
        for (ChatbotHistoryMessageDTO previous : history) {
            appendMessage(input, previous.role(), previous.content());
        }
        appendMessage(input, "user", message);
        addTools(payload, context, toolService);

        Optional<JsonNode> firstResponse = send(payload);
        if (firstResponse.isEmpty()) {
            return Optional.empty();
        }

        TokenUsage totalUsage = tokenUsage(firstResponse.get());
        List<JsonNode> functionCalls = functionCalls(firstResponse.get());
        if (functionCalls.isEmpty() || toolService == null) {
            return outputResult(firstResponse.get(), totalUsage, List.of());
        }

        List<String> usedTools = new ArrayList<>();
        List<String> toolAnswers = new ArrayList<>();
        for (JsonNode functionCall : functionCalls) {
            String toolName = functionCall.path("name").asText();
            try {
                if (!toolService.availableToolNames(context).contains(toolName)) {
                    throw new IllegalArgumentException("Tool is not available for this user.");
                }
                ChatbotToolResult result = toolService.execute(toolName, context);
                toolAnswers.add(result.answer());
                usedTools.add(toolName);
                metrics.recordToolCall(toolName, "success");
            } catch (Exception exception) {
                metrics.recordToolCall(toolName, "failure");
            }
        }
        if (toolAnswers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OpenAiChatbotResult(
                String.join("\n\n", toolAnswers),
                totalUsage.input(),
                totalUsage.output(),
                totalUsage.total(),
                usedTools
        ));
    }

    private ObjectNode basePayload(String instructions) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("instructions", instructions);
        payload.put("store", false);
        payload.put("max_output_tokens", maxOutputTokens);
        return payload;
    }

    private void addTools(
            ObjectNode payload,
            ChatbotUserContext context,
            ChatbotToolService toolService
    ) {
        if (toolService == null) {
            return;
        }
        List<String> toolNames = toolService.availableToolNames(context);
        if (toolNames.isEmpty()) {
            return;
        }
        ArrayNode tools = payload.putArray("tools");
        for (String toolName : toolNames) {
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            tool.put("name", toolName);
            tool.put("description", toolDescription(toolName));
            tool.put("strict", true);
            ObjectNode parameters = tool.putObject("parameters");
            parameters.put("type", "object");
            parameters.putObject("properties");
            parameters.putArray("required");
            parameters.put("additionalProperties", false);
        }
    }

    private String toolDescription(String toolName) {
        return switch (toolName) {
            case ChatbotToolService.GET_MY_RESERVATIONS ->
                    "로그인한 고객 본인의 최근 예약 시간과 상태를 읽기 전용으로 조회합니다.";
            case ChatbotToolService.GET_MY_PAYMENTS ->
                    "로그인한 고객 본인의 최근 결제 금액과 상태를 읽기 전용으로 조회합니다.";
            case ChatbotToolService.GET_MY_WASH_HISTORY ->
                    "로그인한 고객 본인의 최근 세차 내역을 읽기 전용으로 조회합니다.";
            case ChatbotToolService.GET_MY_BALANCES ->
                    "로그인한 고객 본인의 선불 잔액, 마일리지와 쿠폰 수를 읽기 전용으로 조회합니다.";
            default -> "로그인한 고객 본인의 정보를 읽기 전용으로 조회합니다.";
        };
    }

    private Optional<JsonNode> send(ObjectNode payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload)
                    ))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAI chatbot request failed. status={}", response.statusCode());
                metrics.recordOpenAiFailure("http_" + response.statusCode());
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            TokenUsage usage = tokenUsage(body);
            metrics.recordOpenAiUsage(usage.input(), usage.output(), usage.total());
            return Optional.of(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("OpenAI chatbot request was interrupted");
            metrics.recordOpenAiFailure("interrupted");
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("OpenAI chatbot request could not be completed: {}",
                    exception.getClass().getSimpleName());
            metrics.recordOpenAiFailure(exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void appendMessage(ArrayNode input, String role, String content) {
        ObjectNode item = input.addObject();
        item.put("role", role);
        item.put("content", content);
    }

    private Optional<OpenAiChatbotResult> outputResult(
            JsonNode response,
            TokenUsage usage,
            List<String> toolNames
    ) {
        Optional<String> answer = extractOutputText(response);
        return answer.map(value -> new OpenAiChatbotResult(
                value,
                usage.input(),
                usage.output(),
                usage.total(),
                toolNames
        ));
    }

    private Optional<String> extractOutputText(JsonNode response) {
        JsonNode output = response.path("output");
        if (!output.isArray()) {
            return Optional.empty();
        }
        StringBuilder answer = new StringBuilder();
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) {
                    continue;
                }
                String text = content.path("text").asText().trim();
                if (!text.isBlank()) {
                    if (!answer.isEmpty()) {
                        answer.append('\n');
                    }
                    answer.append(text);
                }
            }
        }
        String resolved = answer.toString().trim();
        return resolved.isBlank() ? Optional.empty() : Optional.of(resolved);
    }

    private List<JsonNode> functionCalls(JsonNode response) {
        List<JsonNode> calls = new ArrayList<>();
        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                if ("function_call".equals(item.path("type").asText())) {
                    calls.add(item);
                }
            }
        }
        return calls;
    }

    private TokenUsage tokenUsage(JsonNode response) {
        JsonNode usage = response.path("usage");
        return new TokenUsage(
                usage.path("input_tokens").asInt(),
                usage.path("output_tokens").asInt(),
                usage.path("total_tokens").asInt()
        );
    }

    private record TokenUsage(int input, int output, int total) {
    }
}
