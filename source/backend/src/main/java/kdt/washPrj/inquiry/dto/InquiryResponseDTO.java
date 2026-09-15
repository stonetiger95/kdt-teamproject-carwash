package kdt.washPrj.inquiry.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class InquiryResponseDTO {

    private Long inquiryId;

    private String title;

    private String content;

    private String writerId;

    private String writerName;

    private String writerRole;

    private String status;

    private String answer;

    private String inquiryType;

    private Long paymentHistoryId;

    private String paymentPurpose;

    private Integer paymentAmount;

    private Integer refundedAmount;

    private Integer refundableAmount;

    private Long washSiteId;

    private String siteName;

    private String siteAddress;

    private String sitePhone;

    private Integer answerCount;

    private List<InquiryAnswerResponseDTO> answers;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime answeredAt;

    @JsonIgnore
    private String answersJson;

}
