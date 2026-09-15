package kdt.washPrj.chatbot.dto;

public record ChatbotResponseDTO(
        String answer,
        String source,
        ChatbotWashSiteMapDTO washSite
) {
    public ChatbotResponseDTO(String answer, String source) {
        this(answer, source, null);
    }
}
