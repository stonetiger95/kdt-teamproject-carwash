package kdt.washPrj.payment.dto;

public enum RefundReasonCode {
    CUSTOMER_REQUEST,
    SERVICE_FAILURE,
    DUPLICATE_PAYMENT,
    SYSTEM_ERROR,
    POLICY_VIOLATION,
    FRAUD_SUSPECTED;

    private static final int MAX_REASON_LENGTH = 500;

    public String formatReason(String reason) {
        String detail = reason == null ? "" : reason.trim();
        if (this == CUSTOMER_REQUEST) {
            return truncate(detail, MAX_REASON_LENGTH);
        }
        String prefix = "[" + name() + "]";
        if (detail.isEmpty()) {
            return prefix;
        }
        return prefix + " " + truncate(
                detail,
                MAX_REASON_LENGTH - prefix.length() - 1
        );
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
