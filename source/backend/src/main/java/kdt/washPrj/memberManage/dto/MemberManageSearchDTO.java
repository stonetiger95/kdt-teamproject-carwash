package kdt.washPrj.memberManage.dto;

import lombok.Data;

@Data
public class MemberManageSearchDTO {
    private int page = 1;
    private int size = 10;
    private String keyword;
    private String status;
    private String customerType;

    public int getOffset() {
        return (page - 1) * size;
    }
}
