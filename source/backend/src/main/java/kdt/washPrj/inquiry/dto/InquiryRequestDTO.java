package kdt.washPrj.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InquiryRequestDTO {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String content;

    @Size(max = 100)
    private String writerName;

    @Pattern(regexp = "REFUND|WASH_SITE|SERVICE|ETC")
    private String inquiryType;

    @Positive
    private Long paymentHistoryId;

    @Positive
    private Long washSiteId;

}
