package kdt.washPrj.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatbotFaqRequestDTO(
        @NotBlank @Size(max = 300) String question,
        @NotBlank @Size(max = 2000) String answer,
        @Size(max = 1000) String keywords,
        Boolean enabled,
        Integer displayOrder
) {
}
