package kdt.washPrj.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatbotHistoryMessageDTO(
        @NotBlank
        @Pattern(regexp = "user|assistant")
        String role,

        @NotBlank
        @Size(max = 1000)
        String content
) {
}
