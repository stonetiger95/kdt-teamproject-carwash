package kdt.washPrj.chatbot;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import kdt.washPrj.chatbot.dto.ChatbotMetricSummaryDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ChatbotMetrics {

    private final MeterRegistry registry;
    private final double inputCostPerTokenUsd;
    private final double outputCostPerTokenUsd;
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder failedRequestCount = new LongAdder();
    private final LongAdder totalResponseNanos = new LongAdder();
    private final LongAdder openAiFailureCount = new LongAdder();
    private final LongAdder openAiInputTokens = new LongAdder();
    private final LongAdder openAiOutputTokens = new LongAdder();
    private final DoubleAdder estimatedOpenAiCostUsd = new DoubleAdder();
    private final LongAdder toolCallCount = new LongAdder();
    private final LongAdder rateLimitRejectionCount = new LongAdder();

    public ChatbotMetrics(
            MeterRegistry registry,
            @Value("${chatbot.openai.input-cost-per-million-usd:0}") double inputCostPerMillionUsd,
            @Value("${chatbot.openai.output-cost-per-million-usd:0}") double outputCostPerMillionUsd
    ) {
        this.registry = registry;
        this.inputCostPerTokenUsd = Math.max(0, inputCostPerMillionUsd) / 1_000_000d;
        this.outputCostPerTokenUsd = Math.max(0, outputCostPerMillionUsd) / 1_000_000d;
    }

    public void recordRequest(String source, String outcome, long elapsedNanos) {
        requestCount.increment();
        totalResponseNanos.add(Math.max(0, elapsedNanos));
        if (!"success".equalsIgnoreCase(outcome)) {
            failedRequestCount.increment();
        }
        String safeSource = tag(source);
        String safeOutcome = tag(outcome);
        Counter.builder("chatbot.requests")
                .description("Chatbot requests by answer source and outcome")
                .tags("source", safeSource, "outcome", safeOutcome)
                .register(registry)
                .increment();
        Timer.builder("chatbot.request.duration")
                .description("End-to-end chatbot response time")
                .tags("source", safeSource, "outcome", safeOutcome)
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofNanos(Math.max(0, elapsedNanos)));
    }

    public void recordOpenAiUsage(int inputTokens, int outputTokens, int totalTokens) {
        openAiInputTokens.add(Math.max(0, inputTokens));
        openAiOutputTokens.add(Math.max(0, outputTokens));
        increment("chatbot.openai.tokens", "type", "input", inputTokens);
        increment("chatbot.openai.tokens", "type", "output", outputTokens);
        increment("chatbot.openai.tokens", "type", "total", totalTokens);

        double estimatedCost = Math.max(0, inputTokens) * inputCostPerTokenUsd
                + Math.max(0, outputTokens) * outputCostPerTokenUsd;
        estimatedOpenAiCostUsd.add(estimatedCost);
        increment("chatbot.openai.estimated.cost.usd", "type", "configured", estimatedCost);
    }

    public void recordOpenAiFailure(String reason) {
        openAiFailureCount.increment();
        increment("chatbot.openai.failures", "reason", tag(reason), 1);
    }

    public void recordToolCall(String toolName, String outcome) {
        toolCallCount.increment();
        Counter.builder("chatbot.tool.calls")
                .description("Read-only chatbot tool calls")
                .tags("tool", tag(toolName), "outcome", tag(outcome))
                .register(registry)
                .increment();
    }

    public void recordRateLimitRejection() {
        rateLimitRejectionCount.increment();
        increment("chatbot.rate.limit.rejections", "type", "request", 1);
    }

    public ChatbotMetricSummaryDTO snapshot() {
        long requests = requestCount.sum();
        double averageMilliseconds = requests == 0
                ? 0
                : totalResponseNanos.sum() / 1_000_000d / requests;
        return new ChatbotMetricSummaryDTO(
                requests,
                failedRequestCount.sum(),
                averageMilliseconds,
                openAiFailureCount.sum(),
                openAiInputTokens.sum(),
                openAiOutputTokens.sum(),
                estimatedOpenAiCostUsd.sum(),
                toolCallCount.sum(),
                rateLimitRejectionCount.sum()
        );
    }

    private void increment(String name, String tagName, String tagValue, double amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(name)
                .tag(tagName, tagValue)
                .register(registry)
                .increment(amount);
    }

    private String tag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }
}
