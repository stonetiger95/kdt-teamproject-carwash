package kdt.washPrj.payment;

import kdt.washPrj.payment.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentQueryMapper {

    @Select("""
        SELECT app_api.get_actor_pk(CAST(#{loginId} AS varchar), 'ROLE_CUSTOMER')
    """)
    Long selectCustomerIdByLoginId(@Param("loginId") String loginId);

    @Select("""
        SELECT * FROM app_api.get_prepaid_charge_default(CAST(#{customerId} AS bigint))
    """)
    PaymentWaitingDTO selectPrepaidChargeDefault(@Param("customerId") Long customerId);

    @Select("""
        SELECT * FROM app_api.get_internal_payment(CAST(#{paymentId} AS bigint), FALSE)
    """)
    PaymentResponseDTO selectPaymentById(@Param("paymentId") Long paymentId);

    @Select("""
        SELECT * FROM app_api.get_internal_payment(CAST(#{paymentId} AS bigint), TRUE)
    """)
    PaymentResponseDTO selectPaymentByIdForUpdate(@Param("paymentId") Long paymentId);

    @Select("""
        SELECT *
        FROM app_api.get_payment_provider_context(
            CAST(#{paymentId} AS bigint), CAST(#{provider} AS varchar)
        )
    """)
    PaymentProviderContextDTO selectPaymentProviderContext(
            @Param("paymentId") Long paymentId,
            @Param("provider") String provider
    );

    @Select("""
        SELECT *
        FROM app_api.get_payment_refund_target_for_update(CAST(#{paymentId} AS bigint))
    """)
    PaymentRefundTargetDTO selectPaymentRefundTargetForUpdate(@Param("paymentId") Long paymentId);

    @Select("""
        SELECT *
        FROM app_api.get_prepaid_charge_refund_target_for_update(CAST(#{paymentId} AS bigint))
    """)
    PaymentRefundTargetDTO selectPrepaidChargeRefundTargetForUpdate(@Param("paymentId") Long paymentId);

    @Select("""
        SELECT *
        FROM app_api.get_prepaid_use_refund_target_for_update(CAST(#{paymentId} AS bigint))
    """)
    PaymentRefundTargetDTO selectPrepaidUseRefundTargetForUpdate(@Param("paymentId") Long paymentId);

    @Select("""
        SELECT app_api.get_reserved_refund_amount(CAST(#{paymentId} AS bigint))
    """)
    Integer selectReservedRefundAmount(@Param("paymentId") Long paymentId);

    @Select("""
        SELECT app_api.successful_refund_exists(
            CAST(#{paymentId} AS bigint), CAST(#{amount} AS integer)
        )
    """)
    boolean existsSuccessfulRefund(
            @Param("paymentId") Long paymentId,
            @Param("amount") Integer amount
    );

    @Select("""
        SELECT app_api.active_refund_for_inquiry_exists(CAST(#{inquiryId} AS bigint))
    """)
    boolean existsActiveRefundForInquiry(@Param("inquiryId") Long inquiryId);

    @Select("""
        SELECT *
        FROM app_api.get_prepaid_charge_by_id_for_update(CAST(#{chargeId} AS bigint))
    """)
    PaymentResponseDTO selectPrepaidChargeByIdForUpdate(@Param("chargeId") Long chargeId);

    @Select("""
        SELECT *
        FROM app_api.get_prepaid_charge_by_payment(CAST(#{paymentHistoryId} AS bigint))
    """)
    PaymentResponseDTO selectPrepaidChargeByPaymentHistoryId(
            @Param("paymentHistoryId") Long paymentHistoryId
    );

    @Select("""
        SELECT *
        FROM app_api.get_payment_list(
            CAST(#{loginId} AS varchar),
            CAST(#{condition.status} AS varchar),
            CAST(#{condition.purpose} AS varchar),
            CAST(#{condition.method} AS varchar),
            CAST(#{condition.washSiteId} AS bigint),
            CAST(#{condition.startDate} AS timestamp),
            CAST(#{condition.endDate} AS timestamp),
            CAST(#{condition.keyword} AS varchar)
        )
    """)
    List<PaymentResponseDTO> selectAuthorizedPaymentList(
            @Param("loginId") String loginId,
            @Param("condition") PaymentSearchCondition condition
    );

    @Select("""
        SELECT *
        FROM app_api.get_payment_detail(
            CAST(#{loginId} AS varchar), CAST(#{paymentId} AS bigint)
        )
    """)
    PaymentResponseDTO selectAuthorizedPaymentById(
            @Param("loginId") String loginId,
            @Param("paymentId") Long paymentId
    );

    @Select("""
        SELECT app_api.owner_payment_exists(
            CAST(#{loginId} AS varchar), CAST(#{paymentId} AS bigint)
        )
    """)
    boolean existsOwnerPayment(
            @Param("loginId") String loginId,
            @Param("paymentId") Long paymentId
    );

    @Select("""
        SELECT app_api.lock_non_member_wash_history(CAST(#{washHistoryId} AS bigint))
    """)
    Long lockNonMemberWashHistory(@Param("washHistoryId") Long washHistoryId);

    @Select("""
        SELECT app_api.non_member_waiting_payment_exists(CAST(#{washHistoryId} AS bigint))
    """)
    boolean existsNonMemberWaitingPayment(@Param("washHistoryId") Long washHistoryId);

    @Select("""
        SELECT *
        FROM app_api.get_non_member_checkout_default(CAST(#{washHistoryId} AS bigint))
    """)
    PaymentWaitingDTO selectNonMemberCheckoutDefault(@Param("washHistoryId") Long washHistoryId);

    @Select("""
        SELECT *
        FROM app_api.get_non_member_payment_for_feedback(
            CAST(#{paymentId} AS bigint), CAST(#{washHistoryId} AS bigint)
        )
    """)
    PaymentResponseDTO selectNonMemberPaymentForFeedback(
            @Param("paymentId") Long paymentId,
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT *
        FROM app_api.get_non_member_payment_list(CAST(#{washHistoryId} AS bigint))
    """)
    List<PaymentResponseDTO> selectNonMemberPaymentList(
            @Param("washHistoryId") Long washHistoryId
    );
}
