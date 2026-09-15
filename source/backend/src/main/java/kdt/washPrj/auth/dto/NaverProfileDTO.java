package kdt.washPrj.auth.dto;

import java.time.LocalDate;

public record NaverProfileDTO(
        String providerUserId,
        String name,
        String emailAddress,
        String phone,
        String gender,
        LocalDate birthDate
) {
}
