package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentSearchCondition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PaymentQueryService {

    private final PaymentQueryMapper paymentQueryMapper;

    public PaymentQueryService(PaymentQueryMapper paymentQueryMapper) {
        this.paymentQueryMapper = paymentQueryMapper;
    }

    public List<PaymentResponseDTO> getPaymentList(
            String loginId,
            String role,
            PaymentSearchCondition condition
    ) {
        PaymentSearchCondition normalizedCondition =
                normalizeSearchCondition(condition);
        return paymentQueryMapper.selectAuthorizedPaymentList(
                loginId,
                normalizedCondition
        );
    }

    public PaymentResponseDTO getPayment(
            Long paymentId,
            String loginId,
            String role
    ) {
        PaymentResponseDTO payment =
                paymentQueryMapper.selectAuthorizedPaymentById(loginId, paymentId);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return payment;
    }

    private PaymentSearchCondition normalizeSearchCondition(
            PaymentSearchCondition condition
    ) {
        PaymentSearchCondition normalized = condition == null
                ? new PaymentSearchCondition()
                : condition;

        normalized.setStatus(normalizeUpperFilter(normalized.getStatus()));
        normalized.setPurpose(normalizeUpperFilter(normalized.getPurpose()));
        normalized.setMethod(normalizeUpperFilter(normalized.getMethod()));
        normalized.setKeyword(normalizeTextFilter(normalized.getKeyword()));

        if (normalized.getStartDate() != null
                && normalized.getEndDate() != null
                && normalized.getStartDate().isAfter(normalized.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate must be before or equal to endDate."
            );
        }

        return normalized;
    }

    private String normalizeUpperFilter(String value) {
        String normalized = normalizeTextFilter(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String normalizeTextFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search text must not exceed 100 characters."
            );
        }
        return normalized;
    }

}
