package kdt.washPrj.prepaid;

import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.prepaid.dto.BillingCardDTO;
import kdt.washPrj.prepaid.dto.BillingCardAuthRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargePreparationDTO;
import kdt.washPrj.prepaid.dto.PrepaidWalletDTO;
import kdt.washPrj.prepaid.dto.PrepaidWashPaymentTargetDTO;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PrepaidMapper {

    @Select("""
        SELECT *
        FROM app_api.get_or_create_prepaid_wallet(
            CAST(#{loginId} AS varchar)
        )
    """)
    PrepaidWalletDTO selectAuthorizedWallet(
            @Param("loginId") String loginId
    );

    @Select("""
        SELECT *
        FROM app_api.get_customer_billing_cards(
            CAST(#{loginId} AS varchar)
        )
    """)
    List<BillingCardDTO> selectAuthorizedBillingCards(
            @Param("loginId") String loginId
    );

    @Select("""
        SELECT app_api.apply_confirmed_prepaid_charge(
            CAST(#{paymentHistoryId} AS bigint),
            CAST(#{feedbackAmount} AS integer)
        )
    """)
    int applyConfirmedCharge(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("feedbackAmount") Integer feedbackAmount
    );

    @Select("""
        SELECT *
        FROM app_api.execute_prepaid_wash_payment(
            CAST(#{customerId} AS bigint),
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    PrepaidUseResponseDTO executePrepaidWashPayment(
            @Param("customerId") Long customerId,
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT *
        FROM app_api.execute_my_prepaid_wash_payment(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    PrepaidUseResponseDTO executeMyPrepaidWashPayment(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT *
        FROM app_api.prepare_prepaid_charge_request(
            CAST(#{loginId} AS varchar),
            CAST(#{provider} AS varchar),
            CAST(#{amount} AS integer)
        )
    """)
    PrepaidChargePreparationDTO prepareChargeRequest(
            @Param("loginId") String loginId,
            @Param("provider") String provider,
            @Param("amount") Integer amount
    );

    @Select("""
        SELECT *
        FROM app_api.get_payment_detail(
            CAST(#{loginId} AS varchar), CAST(#{paymentHistoryId} AS bigint)
        )
    """)
    PaymentResponseDTO selectAuthorizedPaymentById(
            @Param("loginId") String loginId,
            @Param("paymentHistoryId") Long paymentHistoryId
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

    @Select("SELECT app_api.reserve_payment_history_id()")
    Long reservePaymentHistoryId();

    @Select("""
        SELECT app_api.insert_successful_payment(
            CAST(#{paymentHistoryId} AS bigint),
            NULL,
            CAST(#{customerId} AS bigint),
            CAST(#{amount} AS integer),
            CAST(#{paymentType} AS varchar),
            NULL,
            'CHARGE',
            NULL
        )
    """)
    int insertSuccessfulTestChargePayment(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("customerId") Long customerId,
            @Param("amount") Integer amount,
            @Param("paymentType") String paymentType
    );

    @Select("""
        SELECT app_api.get_actor_pk(
            CAST(#{loginId} AS varchar),
            'ROLE_CUSTOMER'
        )
    """)
    Long selectCustomerIdByLoginId(@Param("loginId") String loginId);

    @Select("""
        SELECT app_api.get_customer_phone(
            CAST(#{customerId} AS bigint)
        )
    """)
    String selectCustomerPhoneByCustomerId(@Param("customerId") Long customerId);

    @Select("SELECT app_api.ensure_prepaid_wallet(CAST(#{customerId} AS bigint))")
    int insertWalletIfAbsent(@Param("customerId") Long customerId);

    @Select("""
        SELECT *
        FROM app_api.get_prepaid_wallet_by_customer(
            CAST(#{customerId} AS bigint),
            TRUE
        )
    """)
    PrepaidWalletDTO selectWalletForUpdate(@Param("customerId") Long customerId);

    @Select("""
        SELECT *
        FROM app_api.get_default_billing_card_for_update(
            CAST(#{customerId} AS bigint)
        )
    """)
    BillingCardDTO selectDefaultBillingCardForUpdate(
            @Param("customerId") Long customerId
    );

    @Select("""
        SELECT *
        FROM app_api.get_billing_cards_by_customer(
            CAST(#{customerId} AS bigint)
        )
    """)
    List<BillingCardDTO> selectBillingCardsByCustomerId(
            @Param("customerId") Long customerId
    );

    @Select("""
        SELECT *
        FROM app_api.register_billing_card(
            CAST(#{customerId} AS bigint),
            CAST(#{payAppBillingKey} AS varchar),
            CAST(#{paymentProvider} AS varchar),
            CAST(#{cardName} AS varchar),
            CAST(#{cardNoMasked} AS varchar),
            CAST(#{defaultCard} AS boolean),
            CAST(#{autoPayEnabled} AS boolean),
            CAST(#{maxAutoPayAmount} AS integer)
        )
    """)
    BillingCardDTO registerBillingCard(
            @Param("customerId") Long customerId,
            @Param("payAppBillingKey") String payAppBillingKey,
            @Param("paymentProvider") String paymentProvider,
            @Param("cardName") String cardName,
            @Param("cardNoMasked") String cardNoMasked,
            @Param("defaultCard") boolean defaultCard,
            @Param("autoPayEnabled") boolean autoPayEnabled,
            @Param("maxAutoPayAmount") Integer maxAutoPayAmount
    );

    @Select("""
        SELECT app_api.delete_billing_card(
            CAST(#{customerId} AS bigint),
            CAST(#{payAppCardId} AS bigint)
        )
    """)
    int deleteBillingCard(
            @Param("customerId") Long customerId,
            @Param("payAppCardId") Long payAppCardId
    );

    @Select("""
        SELECT app_api.create_billing_auth_request(
            CAST(#{customerId} AS bigint),
            CAST(#{payAppRequestId} AS varchar),
            CAST(#{amount} AS integer),
            CAST(#{defaultCard} AS boolean),
            CAST(#{autoPayEnabled} AS boolean),
            CAST(#{maxAutoPayAmount} AS integer)
        )
    """)
    Long insertBillingAuthRequest(
            @Param("customerId") Long customerId,
            @Param("payAppRequestId") String payAppRequestId,
            @Param("amount") Integer amount,
            @Param("defaultCard") boolean defaultCard,
            @Param("autoPayEnabled") boolean autoPayEnabled,
            @Param("maxAutoPayAmount") Integer maxAutoPayAmount
    );

    @Select("""
        SELECT *
        FROM app_api.get_billing_auth_request_for_update(
            CAST(#{payAppRequestId} AS varchar)
        )
    """)
    BillingCardAuthRequestDTO selectBillingAuthRequestForUpdate(
            @Param("payAppRequestId") String payAppRequestId
    );

    @Select("""
        SELECT app_api.update_billing_auth_request_result(
            CAST(#{payAppRequestId} AS varchar),
            CAST(#{status} AS varchar),
            CAST(#{resultCode} AS varchar),
            CAST(#{resultMessage} AS varchar),
            CAST(#{payAppBillingKey} AS varchar),
            CAST(#{payAppTransactionId} AS varchar)
        )
    """)
    int updateBillingAuthRequestResult(
            @Param("payAppRequestId") String payAppRequestId,
            @Param("status") String status,
            @Param("resultCode") String resultCode,
            @Param("resultMessage") String resultMessage,
            @Param("payAppBillingKey") String payAppBillingKey,
            @Param("payAppTransactionId") String payAppTransactionId
    );

    @Select("""
        SELECT *
          FROM app_api.get_wash_payment_target_for_update(
              CAST(#{customerId} AS bigint),
              CAST(#{washHistoryId} AS bigint)
          )
    """)
    PrepaidWashPaymentTargetDTO selectWashPaymentTargetForUpdate(
            @Param("customerId") Long customerId,
            @Param("washHistoryId") Long washHistoryId
    );

}
