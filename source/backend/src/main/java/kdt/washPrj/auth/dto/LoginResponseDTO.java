package kdt.washPrj.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDTO {

    private String accessToken;
    private String loginId;
    private String userName;
    private String role;
    private String customerStatus;

    private Integer customerId;
    private Integer ownerId;
}
