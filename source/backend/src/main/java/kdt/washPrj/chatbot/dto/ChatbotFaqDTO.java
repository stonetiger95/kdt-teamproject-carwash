package kdt.washPrj.chatbot.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatbotFaqDTO {
    private Long chatbotFaqId;
    private String question;
    private String answer;
    private String keywords;
    private Boolean enabled;
    private Integer displayOrder;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
