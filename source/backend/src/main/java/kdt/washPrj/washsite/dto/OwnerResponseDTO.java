package kdt.washPrj.washsite.dto;

import lombok.Data;

@Data
public class OwnerResponseDTO {

    private Long ownerId;
    private String ownerName;
    private String businessNumber;
    private String phone;
    private String emailAddress;
    private String status;
    private String businessAddress;
    private Long washSiteCount;
}
