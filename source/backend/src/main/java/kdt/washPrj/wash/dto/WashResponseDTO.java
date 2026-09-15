package kdt.washPrj.wash.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public class WashResponseDTO {

    private Long washHistoryId;

    private Long customerId;
    private Long vehicleId;
    private Long washSiteId;
    private Long courseId;

    private String siteName;
    private String sitePhone;
    private String courseName;
    private Long amount;

    private String plateNumber;

    private LocalDateTime entryTime;
    private LocalDateTime washStartTime;
    private LocalDateTime washEndTime;
    private LocalDateTime exitTime;

    private String customerType;
    private String paymentStatus;
    private String washStatus;

    @JsonIgnore
    private Long totalElements;

    public Long getWashHistoryId() {
        return washHistoryId;
    }

    public void setWashHistoryId(Long washHistoryId) {
        this.washHistoryId = washHistoryId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getWashSiteId() {
        return washSiteId;
    }

    public void setWashSiteId(Long washSiteId) {
        this.washSiteId = washSiteId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public LocalDateTime getWashStartTime() {
        return washStartTime;
    }

    public void setWashStartTime(LocalDateTime washStartTime) {
        this.washStartTime = washStartTime;
    }

    public LocalDateTime getWashEndTime() {
        return washEndTime;
    }

    public void setWashEndTime(LocalDateTime washEndTime) {
        this.washEndTime = washEndTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getWashStatus() {
        return washStatus;
    }

    public void setWashStatus(String washStatus) {
        this.washStatus = washStatus;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSitePhone() {
        return sitePhone;
    }

    public void setSitePhone(String sitePhone) {
        this.sitePhone = sitePhone;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(Long totalElements) {
        this.totalElements = totalElements;
    }
}
