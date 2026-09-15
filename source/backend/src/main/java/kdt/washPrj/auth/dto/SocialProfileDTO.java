package kdt.washPrj.auth.dto;

import java.time.LocalDate;

public record SocialProfileDTO(
        String providerUserId,
        String name,
        String emailAddress,
        String phone,
        String gender,
        LocalDate birthDate
) {
}
