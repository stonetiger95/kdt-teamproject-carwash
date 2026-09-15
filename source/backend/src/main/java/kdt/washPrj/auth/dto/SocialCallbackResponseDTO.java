package kdt.washPrj.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SocialCallbackResponseDTO {

    private String status;
    private LoginResponseDTO login;
    private String registrationToken;
    private SocialProfileDTO profile;
}
