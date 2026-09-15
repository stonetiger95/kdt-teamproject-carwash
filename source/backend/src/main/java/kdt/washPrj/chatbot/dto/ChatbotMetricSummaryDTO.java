package kdt.washPrj.chatbot.dto;

public record ChatbotMetricSummaryDTO(
        long requestCount,
        long failedRequestCount,
        double averageResponseMilliseconds,
        long openAiFailureCount,
        long openAiInputTokens,
        long openAiOutputTokens,
        double estimatedOpenAiCostUsd,
        long toolCallCount,
        long rateLimitRejectionCount
) {
}
