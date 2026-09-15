package kdt.washPrj.chatbot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatbotRequestDTO(
        @NotBlank
        @Size(max = 1000)
        String message,

        @Size(max = 8)
        List<@Valid ChatbotHistoryMessageDTO> history,

        Double latitude,

        Double longitude
) {
    public ChatbotRequestDTO(String message, List<ChatbotHistoryMessageDTO> history) {
        this(message, history, null, null);
    }

    public ChatbotRequestDTO {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
