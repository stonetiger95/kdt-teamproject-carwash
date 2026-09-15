package kdt.washPrj.customer.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class BeginCustomerEmailChangeResultDTO {

    private String resultCode;
    private String destinationEmail;
    private OffsetDateTime challengeExpiresAt;
    private OffsetDateTime resendAvailableAt;
}
