package kdt.washPrj.inquiry.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InquiryAnswerResponseDTO {

    private Long inquiryAnswerId;

    private Long inquiryId;

    private String answer;

    private String answererId;

    private String answererRole;

    private LocalDateTime createdAt;
}
