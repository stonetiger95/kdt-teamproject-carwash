package kdt.washPrj.memberManage.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class MemberManageListDTO {
    private Integer customerId;
    private String loginId;
    private String name;
    private String phone;
    private String customerType;
    private String status;
    private Integer activeVehicleCount;

    @JsonIgnore
    private Long totalElements;
}
