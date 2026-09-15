package kdt.washPrj.chatbot;

import java.util.List;

public record OpenAiChatbotResult(
        String answer,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        List<String> toolNames
) {
    public OpenAiChatbotResult {
        toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
    }
}
