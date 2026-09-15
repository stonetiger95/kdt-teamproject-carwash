package kdt.washPrj.memberManage.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import kdt.washPrj.vehicle.VehicleVO;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MemberManageDetailDTO {
    private Integer customerId;
    private String loginId;
    private String name;
    private LocalDate birthDate;
    private String phone;
    private String customerType;
    private String status;
    private String gender;
    private String emailAddress;
    private Integer vehicleCount;
    private Integer activeVehicleCount;
    private List<VehicleVO> vehicles;

    @JsonIgnore
    private String vehiclesJson;
}
