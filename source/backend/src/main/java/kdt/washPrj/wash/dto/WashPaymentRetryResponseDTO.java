package kdt.washPrj.wash.dto;

public record WashPaymentRetryResponseDTO(
        Long washHistoryId,
        String result,
        String paymentStatus,
        Long amount,
        Integer balance,
        Long paymentHistoryId,
        String message
) {
}
