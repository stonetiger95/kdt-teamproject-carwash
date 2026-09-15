package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotWashSiteMapDTO;

public record ChatbotToolResult(
        String name,
        String output,
        String answer,
        ChatbotWashSiteMapDTO washSite
) {
    public ChatbotToolResult(String name, String output, String answer) {
        this(name, output, answer, null);
    }
}
