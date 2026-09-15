package kdt.washPrj.customer.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CompleteCustomerEmailChangeResultDTO {

    private String resultCode;
    private Integer customerId;
    private String loginId;
    private String name;
    private LocalDate birthDate;
    private String phone;
    private String customerType;
    private String status;
    private String gender;
    private String emailAddress;

    public CustomerMyPageDTO toCustomerMyPage() {
        CustomerMyPageDTO result = new CustomerMyPageDTO();
        result.setCustomerId(customerId);
        result.setLoginId(loginId);
        result.setName(name);
        result.setBirthDate(birthDate);
        result.setPhone(phone);
        result.setCustomerType(customerType);
        result.setStatus(status);
        result.setGender(gender);
        result.setEmailAddress(emailAddress);
        return result;
    }
}
