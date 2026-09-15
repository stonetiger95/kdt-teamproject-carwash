package kdt.washPrj.nonMemberReservation;

import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationAuthRecordDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationCreateDTO;
import kdt.washPrj.nonMemberReservation.dto.NonMemberReservationResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface NonMemberReservationMapper {

    @Select("""
        SELECT app_api.guest_reservation_has_plate_time_conflict(
            #{plateNumber},
            #{startTime},
            #{endTime}
        )
    """)
    boolean existsPlateTimeConflict(
            @Param("plateNumber") String plateNumber,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Select("""
        SELECT app_api.create_guest_pending_reservation(
            CAST(#{dto.washSiteId} AS bigint),
            CAST(#{dto.deviceId} AS bigint),
            CAST(#{dto.courseId} AS bigint),
            CAST(#{dto.startTime} AS timestamp),
            CAST(#{plateNumber} AS varchar),
            CAST(#{phone} AS varchar),
            CAST(#{authCodeHash} AS varchar)
        )
    """)
    Long insertPendingReservation(
            @Param("dto") NonMemberReservationCreateDTO dto,
            @Param("plateNumber") String plateNumber,
            @Param("phone") String phone,
            @Param("authCodeHash") String authCodeHash
    );

    @Select("""
        SELECT *
          FROM app_api.get_guest_reservation_detail(#{reservationId})
    """)
    NonMemberReservationResponseDTO selectReservation(
            @Param("reservationId") Long reservationId
    );

    @Select("""
        SELECT *
          FROM app_api.get_guest_reservation_auth_record(#{reservationId})
    """)
    NonMemberReservationAuthRecordDTO selectAuthRecord(
            @Param("reservationId") Long reservationId
    );

    @Select("""
        SELECT app_api.cancel_guest_pending_reservation(
            #{reservationId}, #{reason}
        )
    """)
    int cancelPendingReservation(
            @Param("reservationId") Long reservationId,
            @Param("reason") String reason
    );

    @Select("""
        SELECT app_api.confirm_guest_payment(#{reservationId})
    """)
    int confirmPayment(@Param("reservationId") Long reservationId);

    @Select("""
        SELECT app_api.fail_guest_payment(#{reservationId}, #{reason})
    """)
    int failPayment(
            @Param("reservationId") Long reservationId,
            @Param("reason") String reason
    );

    @Select("""
        SELECT app_api.insert_guest_reservation_history(
            #{reservationId},
            #{eventType},
            #{fromStatus},
            #{toStatus},
            #{actorType},
            #{reason}
        )
    """)
    int insertHistory(
            @Param("reservationId") Long reservationId,
            @Param("eventType") String eventType,
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("actorType") String actorType,
            @Param("reason") String reason
    );


    @Select("SELECT app_api.expire_guest_reservation_payments()")
    int expireGuestReservationPayments();
}
