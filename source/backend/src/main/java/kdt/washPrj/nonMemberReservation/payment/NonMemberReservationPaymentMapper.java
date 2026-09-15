package kdt.washPrj.nonMemberReservation.payment;

import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentRecordDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.NonMemberReservationPaymentTargetDTO;
import kdt.washPrj.nonMemberReservation.payment.dto.GuestReservationRefundPreparationDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NonMemberReservationPaymentMapper {

    @Select("""
        SELECT nextval(
            pg_get_serial_sequence(
                'public.payment_history',
                'payment_history_id'
            )
        )
        """)
    Long reservePaymentHistoryId();

    @Select("""
        SELECT *
          FROM app_api.get_guest_reservation_payment_target_for_update(
              #{reservationId}
          )
        """)
    NonMemberReservationPaymentTargetDTO
    selectPaymentTargetForUpdate(
            @Param("reservationId") Long reservationId
    );

    @Select("""
        SELECT *
          FROM app_api.get_active_guest_reservation_payment_for_update(
              #{reservationId}
          )
        """)
    NonMemberReservationPaymentRecordDTO
    selectActivePaymentForUpdate(
            @Param("reservationId") Long reservationId
    );

    @Select("""
        SELECT *
          FROM app_api.get_guest_reservation_payment_for_update(
              #{paymentHistoryId},
              #{reservationId}
          )
        """)
    NonMemberReservationPaymentRecordDTO
    selectPaymentForUpdate(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("reservationId") Long reservationId
    );

    @Insert("""
        INSERT INTO payment_history (
            payment_history_id,
            reservation_id,
            wash_history_id,
            customer_id,
            amount,
            payment_type,
            status,
            paid_at,
            purpose
        )
        OVERRIDING SYSTEM VALUE
        VALUES (
            #{paymentHistoryId},
            #{reservationId},
            NULL,
            NULL,
            #{amount},
            'PAYAPP',
            'WAITING',
            NULL,
            'RESERVATION_PAYMENT'
        )
        ON CONFLICT DO NOTHING
        """)
    int insertWaitingPayment(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("reservationId") Long reservationId,
            @Param("amount") Integer amount
    );

    @Update("""
        UPDATE payment_history
        SET status = 'FAILED',
            canceled_at = CURRENT_TIMESTAMP,
            cancel_reason = #{reason}
        WHERE payment_history_id = #{paymentHistoryId}
          AND reservation_id = #{reservationId}
          AND wash_history_id IS NULL
          AND customer_id IS NULL
          AND purpose = 'RESERVATION_PAYMENT'
          AND status = 'WAITING'
        """)
    int failPaymentRequest(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("reservationId") Long reservationId,
            @Param("reason") String reason
    );

    @Update("""
        UPDATE payment_history
        SET payment_type = COALESCE(#{paymentType}, payment_type),
            status = #{paymentStatus},
            paid_at = CASE
                WHEN #{paymentStatus} = 'SUCCESS'
                    THEN COALESCE(#{paidAt}, CURRENT_TIMESTAMP)
                ELSE paid_at
            END,
            payapp_payment_id = COALESCE(
                #{payAppPaymentId},
                payapp_payment_id
            ),
            canceled_at = CASE
                WHEN #{paymentStatus} IN ('FAILED', 'CANCELED')
                    THEN COALESCE(#{paidAt}, CURRENT_TIMESTAMP)
                WHEN #{paymentStatus} = 'SUCCESS' THEN NULL
                ELSE canceled_at
            END,
            cancel_reason = CASE
                WHEN #{paymentStatus} IN ('FAILED', 'CANCELED')
                    THEN COALESCE(#{cancelReason}, cancel_reason)
                WHEN #{paymentStatus} = 'SUCCESS' THEN NULL
                ELSE cancel_reason
            END
        WHERE payment_history_id = #{paymentHistoryId}
          AND reservation_id = #{reservationId}
          AND wash_history_id IS NULL
          AND customer_id IS NULL
          AND purpose = 'RESERVATION_PAYMENT'
          AND status = 'WAITING'
        """)
    int updateFromFeedback(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("reservationId") Long reservationId,
            @Param("paymentType") String paymentType,
            @Param("paymentStatus") String paymentStatus,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("cancelReason") String cancelReason
    );


    @Select("""
        SELECT app_api.fail_expired_guest_waiting_payments()
        """)
    int failExpiredWaitingPayments();

    @Select("""
        SELECT reservation_id
          FROM app_api.get_guest_no_show_refund_candidates(
              #{limit}
          )
        """)
    List<Long> selectGuestNoShowRefundCandidates(
            @Param("limit") int limit
    );

    @Select("""
        SELECT *
          FROM app_api.prepare_guest_no_show_refund(
              #{reservationId},
              #{cancelReason}
          )
        """)
    GuestReservationRefundPreparationDTO prepareGuestNoShowRefund(
            @Param("reservationId") Long reservationId,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.complete_guest_no_show_refund(
            #{refundHistoryId},
            #{providerResponse}
        )
        """)
    int completeGuestNoShowRefund(
            @Param("refundHistoryId") Long refundHistoryId,
            @Param("providerResponse") String providerResponse
    );

    @Select("""
        SELECT *
          FROM app_api.prepare_guest_reservation_refund(
              #{reservationId},
              #{cancelReason}
          )
        """)
    GuestReservationRefundPreparationDTO prepareGuestReservationRefund(
            @Param("reservationId") Long reservationId,
            @Param("cancelReason") String cancelReason
    );

    @Select("""
        SELECT app_api.complete_guest_reservation_refund(
            #{refundHistoryId},
            #{providerResponse}
        )
        """)
    int completeGuestReservationRefund(
            @Param("refundHistoryId") Long refundHistoryId,
            @Param("providerResponse") String providerResponse
    );

    @Select("""
        SELECT app_api.fail_guest_reservation_refund(
            #{refundHistoryId},
            #{providerResponse}
        )
        """)
    int failGuestReservationRefund(
            @Param("refundHistoryId") Long refundHistoryId,
            @Param("providerResponse") String providerResponse
    );

    @Select("""
        SELECT app_api.apply_guest_reservation_refund_feedback(
            #{paymentHistoryId},
            #{reservationId},
            #{amount},
            #{payAppPaymentId},
            #{providerResponse}
        )
        """)
    String applyGuestReservationRefundFeedback(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("reservationId") Long reservationId,
            @Param("amount") Integer amount,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("providerResponse") String providerResponse
    );

    @Select("""
        SELECT app_api.apply_guest_no_show_refund_feedback(
            #{paymentHistoryId},
            #{reservationId},
            #{amount},
            #{payAppPaymentId},
            #{providerResponse}
        )
        """)
    String applyGuestNoShowRefundFeedback(
            @Param("paymentHistoryId") Long paymentHistoryId,
            @Param("reservationId") Long reservationId,
            @Param("amount") Integer amount,
            @Param("payAppPaymentId") String payAppPaymentId,
            @Param("providerResponse") String providerResponse
    );

    @Select("""
        SELECT app_api.mark_guest_reservation_refund_reconciliation_required(
            #{refundHistoryId},
            #{providerResponse}
        )
        """)
    int markGuestReservationRefundReconciliationRequired(
            @Param("refundHistoryId") Long refundHistoryId,
            @Param("providerResponse") String providerResponse
    );
}
