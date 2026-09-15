package kdt.washPrj.nonMember.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class NonMemberAccessResponseDTO {

    private String accessToken;
    private Instant expiresAt;
}
