package kdt.washPrj.chatbot.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatbotUnresolvedQuestionDTO {
    private Long chatbotUnresolvedQuestionId;
    private String maskedQuestion;
    private String reason;
    private String status;
    private String resolvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
