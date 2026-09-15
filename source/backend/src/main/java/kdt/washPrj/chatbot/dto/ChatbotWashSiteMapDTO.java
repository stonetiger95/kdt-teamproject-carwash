package kdt.washPrj.chatbot.dto;

public record ChatbotWashSiteMapDTO(
        Long washSiteId,
        String siteName,
        String address,
        Double latitude,
        Double longitude
) {
}
