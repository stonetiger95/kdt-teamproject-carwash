package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentWaitingDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NonMemberPaymentAttemptService {

    private final PaymentQueryMapper paymentQueryMapper;
    private final PaymentCommandMapper paymentCommandMapper;

    public NonMemberPaymentAttemptService(
            PaymentQueryMapper paymentQueryMapper,
            PaymentCommandMapper paymentCommandMapper
    ) {
        this.paymentQueryMapper = paymentQueryMapper;
        this.paymentCommandMapper = paymentCommandMapper;
    }

    @Transactional
    public Reservation reserve(Long washHistoryId) {
        Long lockedWashHistoryId =
                paymentQueryMapper.lockNonMemberWashHistory(washHistoryId);

        if (lockedWashHistoryId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Non-member wash history was not found."
            );
        }

        if (paymentQueryMapper.existsNonMemberWaitingPayment(washHistoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A non-member payment is already in progress."
            );
        }

        PaymentWaitingDTO checkoutDefault =
                paymentQueryMapper.selectNonMemberCheckoutDefault(washHistoryId);

        if (checkoutDefault == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Payable non-member wash history was not found."
            );
        }

        if (checkoutDefault.getAmount() == null
                || checkoutDefault.getAmount() < 1000
                || checkoutDefault.getOrderName() == null
                || checkoutDefault.getOrderName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The non-member payment target is invalid."
            );
        }

        Long paymentHistoryId = paymentCommandMapper.reservePaymentHistoryId();
        int inserted = paymentCommandMapper.insertNonMemberPaymentWaiting(
                paymentHistoryId,
                washHistoryId,
                checkoutDefault.getAmount()
        );

        if (inserted != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The non-member payment request could not be reserved."
            );
        }

        return new Reservation(paymentHistoryId, checkoutDefault);
    }

    public record Reservation(
            Long paymentHistoryId,
            PaymentWaitingDTO checkoutDefault
    ) {
    }
}
