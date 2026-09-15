package kdt.washPrj.washsite.dto;

import lombok.Data;

@Data
public class WashSiteRequestDTO {

    private Long washSiteId;
    private Long ownerId;
    private String siteName;
    private String address;
    private String sitePhone;
    private String status;
    private String courseName;
    private Integer washPrice;
    private Double latitude;
    private Double longitude;
}
