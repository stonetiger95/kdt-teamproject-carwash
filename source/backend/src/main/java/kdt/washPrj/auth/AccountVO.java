package kdt.washPrj.auth;

import lombok.Data;

@Data
public class AccountVO {

    private String loginId;

    private String password;

    private String userName;

    private String role;

    private Integer customerId;
    private Integer ownerId;
    private String customerStatus;
    private Long credentialVersion;
}
