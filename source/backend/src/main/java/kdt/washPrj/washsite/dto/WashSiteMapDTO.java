package kdt.washPrj.washsite.dto;

import lombok.Data;

@Data
public class WashSiteMapDTO {

    private Long washSiteId;
    private String siteName;
    private String address;
    private String sitePhone;
    private String status;
    private Double latitude;
    private Double longitude;
    private Long courseId;
    private String courseName;
    private Integer washPrice;
    private Double distanceKm;
}
