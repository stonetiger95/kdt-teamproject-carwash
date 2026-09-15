package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.PaymentRefundPreparationDTO;
import kdt.washPrj.payment.dto.PaymentRequestPreparationDTO;
import kdt.washPrj.payment.dto.PaymentProviderResultDTO;
import kdt.washPrj.payment.dto.PaymentReconciliationCandidateDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentCommandMapper {

    @Select("""
        SELECT app_api.apply_payment_feedback_and_wash(
            CAST(#{paymentId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{payAppPaymentId} AS varchar),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int applyPaymentFeedbackAndWash(
            @Param("paymentId") Long paymentId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.apply_non_member_payment_feedback_and_wash(
            CAST(#{paymentId} AS bigint),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{payAppPaymentId} AS varchar),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int applyNonMemberPaymentFeedbackAndWash(
            @Param("paymentId") Long paymentId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.apply_prepaid_charge_feedback(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{payAppPaymentId} AS varchar),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int applyPrepaidChargeFeedback(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.cancel_confirmed_prepaid_charge_feedback(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{canceledAt} AS timestamp),
            CAST(#{payAppPaymentId} AS varchar),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int cancelConfirmedPrepaidChargeFeedback(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("canceledAt") LocalDateTime canceledAt,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT *
        FROM app_api.prepare_benefit_payment_refund(
            CAST(#{loginId} AS varchar),
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{requestedAmount} AS integer),
            CAST(#{reason} AS varchar),
            CAST(#{inquiryId} AS bigint)
        )
    """)
    PaymentRefundPreparationDTO preparePaymentRefund(
            @Param("loginId") String loginId,
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("requestedAmount") Integer requestedAmount,
            @Param("reason") String reason,
            @Param("inquiryId") Long inquiryId
    );

    @Select("""
        SELECT app_api.complete_benefit_payment_refund(
            CAST(#{refundHistoryId} AS bigint),
            CAST(#{providerResponse} AS text)
        )
    """)
    int completePaymentRefund(
            @Param("refundHistoryId") Long refundHistoryId,
            @Param("providerResponse") String providerResponse
    );

    @Select("""
        SELECT app_api.complete_inquiry_benefit_refund(
            CAST(#{refundHistoryId} AS bigint),
            CAST(#{providerResponse} AS text),
            CAST(#{answer} AS text)
        )
    """)
    int completeInquiryPaymentRefund(
            @Param("refundHistoryId") Long refundHistoryId,
            @Param("providerResponse") String providerResponse,
            @Param("answer") String answer
    );

    @Select("""
        SELECT app_api.mark_refund_reconciliation_required(
            CAST(#{refundHistoryId} AS bigint),
            CAST(#{providerResponse} AS text)
        )
    """)
    int markRefundReconciliationRequired(
            @Param("refundHistoryId") Long refundHistoryId,
            @Param("providerResponse") String providerResponse
    );

    @Select("SELECT app_api.reserve_payment_history_id()")
    Long reservePaymentHistoryId();

    @Select("""
        SELECT *
        FROM app_api.prepare_non_member_payment_request(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{provider} AS varchar)
        )
    """)
    PaymentRequestPreparationDTO prepareNonMemberPaymentRequest(
            @Param("washHistoryId") Long washHistoryId,
            @Param("provider") String provider
    );

    @Select("""
        SELECT app_api.mark_payment_request_failed(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{provider} AS varchar),
            CAST(#{providerStatus} AS varchar),
            CAST(#{failureReason} AS varchar)
        )
    """)
    int markPaymentRequestFailed(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("provider") String provider,
            @Param("providerStatus") String providerStatus,
            @Param("failureReason") String failureReason
    );

    @Select("SELECT app_api.release_wash_benefit_reservation(CAST(#{washHistoryId} AS bigint))")
    int releaseWashBenefitReservation(
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT *
        FROM app_api.apply_provider_payment_result(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{provider} AS varchar),
            CAST(#{providerTransactionId} AS varchar),
            CAST(#{providerStatus} AS varchar),
            CAST(#{paymentMethod} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    PaymentProviderResultDTO applyProviderPaymentResult(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("provider") String provider,
            @Param("providerTransactionId") String providerTransactionId,
            @Param("providerStatus") String providerStatus,
            @Param("paymentMethod") String paymentMethod,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.record_payment_provider_request(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{provider} AS varchar),
            CAST(#{providerTransactionId} AS varchar),
            CAST(#{providerStatus} AS varchar),
            CAST(#{reconcileAfterSeconds} AS integer)
        )
    """)
    String recordPaymentProviderRequest(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("provider") String provider,
            @Param("providerTransactionId") String providerTransactionId,
            @Param("providerStatus") String providerStatus,
            @Param("reconcileAfterSeconds") Integer reconcileAfterSeconds
    );

    @Select("""
        SELECT
            payment_history_id AS "paymentHistoryId",
            payment_provider AS "paymentProvider",
            provider_transaction_id AS "providerTransactionId",
            amount,
            purpose,
            reconciliation_attempts AS "reconciliationAttempts"
        FROM app_api.claim_payment_reconciliation_candidates(
            CAST(#{limit} AS integer),
            CAST(#{leaseSeconds} AS integer)
        )
    """)
    List<PaymentReconciliationCandidateDTO> claimPaymentReconciliationCandidates(
            @Param("limit") Integer limit,
            @Param("leaseSeconds") Integer leaseSeconds
    );

    @Select("""
        SELECT app_api.complete_payment_reconciliation(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{outcome} AS varchar),
            CAST(#{providerStatus} AS varchar),
            CAST(#{message} AS varchar),
            CAST(#{retryAfterSeconds} AS integer),
            CAST(#{maxAttempts} AS integer)
        )
    """)
    String completePaymentReconciliation(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("outcome") String outcome,
            @Param("providerStatus") String providerStatus,
            @Param("message") String message,
            @Param("retryAfterSeconds") Integer retryAfterSeconds,
            @Param("maxAttempts") Integer maxAttempts
    );

    @Select("""
        SELECT app_api.mark_untrackable_payment_reconciliations(
            CAST(#{staleBefore} AS timestamp),
            CAST(#{limit} AS integer)
        )
    """)
    int markUntrackablePaymentReconciliations(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("limit") Integer limit
    );

    @Select("""
        SELECT app_api.insert_non_member_payment_waiting(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{amount} AS integer)
        )
    """)
    int insertNonMemberPaymentWaiting(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("amount") Integer amount
    );

    @Select("""
        SELECT app_api.insert_successful_payment(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{customerId} AS bigint),
            CAST(#{amount} AS integer),
            CAST(#{method} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{purpose} AS varchar),
            CAST(#{payAppPaymentId} AS varchar)
        )
    """)
    int insertSuccessfulPaymentFromFeedback(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("customerId") Long customerId,
            @Param("amount") Integer amount,
            @Param("method") String method,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("purpose") String purpose,
            @Param("payAppPaymentId") String payAppPaymentId
    );

    @Select("""
        SELECT app_api.set_wash_payment_status_from_payment(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{paymentStatus} AS varchar)
        )
    """)
    int updateWashPaymentStatus(
            @Param("washHistoryId") Long washHistoryId,
            @Param("paymentStatus") String paymentStatus
    );

    @Select("""
        SELECT app_api.update_payment_from_feedback(
            CAST(#{paymentId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{payAppPaymentId} AS varchar),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int updatePaymentFromFeedback(
            @Param("paymentId") Long paymentId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.update_prepaid_charge_feedback_by_charge_id(
            CAST(#{chargeId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int updatePrepaidChargeFromFeedback(
            @Param("chargeId") Long chargeId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.update_prepaid_charge_feedback_by_payment(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int updatePrepaidChargeByPaymentHistoryId(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("cancelReason") String cancelReason
    );

    @Select("SELECT app_api.ensure_prepaid_wallet(CAST(#{customerId} AS bigint))")
    int ensurePrepaidWallet(@Param("customerId") Long customerId);

    @Select("SELECT app_api.increase_prepaid_wallet(CAST(#{customerId} AS bigint), CAST(#{amount} AS integer))")
    int increasePrepaidWallet(@Param("customerId") Long customerId, @Param("amount") Integer amount);

    @Select("SELECT app_api.decrease_prepaid_wallet(CAST(#{walletId} AS bigint), CAST(#{amount} AS integer))")
    int decreasePrepaidWallet(@Param("walletId") Long walletId, @Param("amount") Integer amount);

    @Select("SELECT app_api.reverse_prepaid_wallet_for_cancellation(CAST(#{walletId} AS bigint), CAST(#{amount} AS integer))")
    int reversePrepaidWalletForProviderCancellation(@Param("walletId") Long walletId, @Param("amount") Integer amount);

    @Select("""
        SELECT app_api.cancel_successful_payment(
            CAST(#{paymentId} AS bigint),
            CAST(#{status} AS varchar),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int updatePaymentCanceled(
            @Param("paymentId") Long paymentId,
            @Param("status") String status,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.cancel_successful_prepaid_charge(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int cancelPrepaidCharge(@Param("paymentHistoryId") Long paymentHistoryId, @Param("cancelReason") String cancelReason);

    @Select("""
        SELECT app_api.cancel_successful_prepaid_use(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int cancelPrepaidUse(@Param("washHistoryId") Long washHistoryId, @Param("cancelReason") String cancelReason);

    @Select("SELECT app_api.restore_prepaid_wallet(CAST(#{walletId} AS bigint), CAST(#{amount} AS integer))")
    int restorePrepaidWallet(@Param("walletId") Long walletId, @Param("amount") Integer amount);

    @Select("""
        SELECT app_api.create_payment_refund(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{customerId} AS bigint),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{amount} AS integer),
            CAST(#{refundType} AS varchar),
            CAST(#{requestedBy} AS varchar),
            CAST(#{reason} AS varchar),
            CAST(#{payAppPaymentId} AS varchar)
        )
    """)
    Long insertRefundHistory(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("customerId") Long customerId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("amount") Integer amount,
            @Param("refundType") String refundType,
            @Param("requestedBy") String requestedBy,
            @Param("reason") String reason,
            @Param("payAppPaymentId") String payAppPaymentId
    );

    @Select("""
        SELECT app_api.update_payment_refund_result(
            CAST(#{refundId} AS bigint),
            CAST(#{status} AS varchar),
            CAST(#{providerResponse} AS text)
        )
    """)
    int updateRefundHistoryResult(
            @Param("refundId") Long refundId,
            @Param("status") String status,
            @Param("providerResponse") String providerResponse
    );

    @Select("""
        SELECT app_api.update_non_member_payment_from_feedback(
            CAST(#{paymentId} AS bigint),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{method} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{status} AS varchar),
            CAST(#{paidAt} AS timestamp),
            CAST(#{payAppPaymentId} AS varchar),
            CAST(#{cancelReason} AS varchar)
        )
    """)
    int updateNonMemberPaymentFromFeedback(
            @Param("paymentId") Long paymentId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("method") String method,
            @Param("amount") Integer amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("cancelReason") String cancelReason
    );
}
