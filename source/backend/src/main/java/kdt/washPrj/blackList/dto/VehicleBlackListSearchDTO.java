package kdt.washPrj.blackList.dto;

import lombok.Data;

@Data
public class VehicleBlackListSearchDTO {
    private String keyword = "";
    private String blacklistType = "";
    private Boolean active;
    private int page = 1;
    private int size = 10;

    public int getOffset() {
        return (page - 1) * size;
    }
}
