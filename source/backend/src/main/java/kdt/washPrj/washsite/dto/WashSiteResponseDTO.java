package kdt.washPrj.washsite.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WashSiteResponseDTO {

    private Long washSiteId;
    private Long ownerId;
    private String ownerName;
    private String businessNumber;
    private String siteName;
    private String address;
    private String sitePhone;
    private String status;
    private LocalDateTime createdAt;
    private Long courseId;
    private String courseName;
    private Integer washPrice;
    private Double latitude;
    private Double longitude;
}
