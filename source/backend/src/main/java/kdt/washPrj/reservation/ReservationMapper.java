package kdt.washPrj.reservation;

import kdt.washPrj.reservation.dto.AvailableSlotDTO;
import kdt.washPrj.reservation.dto.OwnerReservationDashboardDTO;
import kdt.washPrj.reservation.dto.ReservationCancelDTO;
import kdt.washPrj.reservation.dto.ReservationCreateDTO;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.ReservationSearchDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    String PAGE_ARGUMENTS = """
        CAST(#{loginId} AS varchar),
        CAST(#{search.washSiteId} AS bigint),
        CAST(#{search.ownerId} AS bigint),
        CAST(#{search.deviceId} AS bigint),
        CAST(#{search.status} AS varchar),
        CAST(#{search.plateNumber} AS varchar),
        CAST(#{search.startDate} AS date),
        CAST(#{search.endDate} AS date)
        """;

    @Select("""
        SELECT *
        FROM app_api.get_available_reservation_slots(
            CAST(#{washSiteId} AS bigint),
            CAST(#{date} AS date)
        )
    """)
    List<AvailableSlotDTO> selectAvailableSlots(
            @Param("washSiteId") Long washSiteId,
            @Param("date") LocalDate date
    );

    @Select("""
        SELECT app_api.create_customer_reservation(
            CAST(#{loginId} AS varchar),
            CAST(#{dto.vehicleId} AS bigint),
            CAST(#{dto.washSiteId} AS bigint),
            CAST(#{dto.deviceId} AS bigint),
            CAST(#{dto.courseId} AS bigint),
            CAST(#{dto.startTime} AS timestamp)
        )
    """)
    Long insertReservation(
            @Param("loginId") String loginId,
            @Param("dto") ReservationCreateDTO dto
    );

    @Select("""
        SELECT *
        FROM app_api.get_customer_reservation_page(
        """ + PAGE_ARGUMENTS + """
        ,
            CAST(#{search.size} AS integer),
            CAST(#{search.offset} AS integer)
        )
    """)
    List<ReservationResponseDTO> selectCustomerReservationList(
            @Param("loginId") String loginId,
            @Param("search") ReservationSearchDTO search
    );

    @Select("""
        SELECT app_api.count_customer_reservations(
        """ + PAGE_ARGUMENTS + """
        )
    """)
    int countCustomerReservationPage(
            @Param("loginId") String loginId,
            @Param("search") ReservationSearchDTO search
    );

    @Select("""
        SELECT *
        FROM app_api.get_customer_reservation_detail(
            CAST(#{loginId} AS varchar),
            CAST(#{reservationId} AS bigint)
        )
    """)
    ReservationResponseDTO selectCustomerReservation(
            @Param("loginId") String loginId,
            @Param("reservationId") Long reservationId
    );

    @Select("""
        SELECT app_api.cancel_customer_reservation(
            CAST(#{loginId} AS varchar),
            CAST(#{reservationId} AS bigint),
            CAST(#{dto.cancelReason} AS varchar)
        )
    """)
    int cancelCustomerReservation(
            @Param("loginId") String loginId,
            @Param("reservationId") Long reservationId,
            @Param("dto") ReservationCancelDTO dto
    );

    @Select("""
        SELECT *
        FROM app_api.get_owner_reservation_page(
        """ + PAGE_ARGUMENTS + """
        ,
            CAST(#{search.size} AS integer),
            CAST(#{search.offset} AS integer)
        )
    """)
    List<ReservationResponseDTO> selectOwnerReservationPage(
            @Param("loginId") String loginId,
            @Param("search") ReservationSearchDTO search
    );

    @Select("""
        SELECT app_api.count_owner_reservations(
        """ + PAGE_ARGUMENTS + """
        )
    """)
    int countOwnerReservationPage(
            @Param("loginId") String loginId,
            @Param("search") ReservationSearchDTO search
    );

    @Select("""
        SELECT *
        FROM app_api.get_owner_reservation_detail(
            CAST(#{loginId} AS varchar),
            CAST(#{reservationId} AS bigint)
        )
    """)
    ReservationResponseDTO selectOwnerReservation(
            @Param("loginId") String loginId,
            @Param("reservationId") Long reservationId
    );

    @Select("""
        SELECT *
        FROM app_api.get_owner_reservation_dashboard(
            CAST(#{loginId} AS varchar),
            CAST(#{date} AS date)
        )
    """)
    OwnerReservationDashboardDTO selectOwnerReservationDashboard(
            @Param("loginId") String loginId,
            @Param("date") LocalDate date
    );

    @Select("""
        SELECT *
        FROM app_api.get_owner_upcoming_reservations(
            CAST(#{loginId} AS varchar),
            CAST(#{date} AS date)
        )
    """)
    List<ReservationResponseDTO> selectOwnerUpcomingReservations(
            @Param("loginId") String loginId,
            @Param("date") LocalDate date
    );

    @Select("""
        SELECT app_api.cancel_owner_reservation(
            CAST(#{loginId} AS varchar),
            CAST(#{reservationId} AS bigint),
            CAST(#{dto.cancelReason} AS varchar)
        )
    """)
    int cancelOwnerReservation(
            @Param("loginId") String loginId,
            @Param("reservationId") Long reservationId,
            @Param("dto") ReservationCancelDTO dto
    );

    @Select("""
        SELECT *
        FROM app_api.get_admin_reservation_page(
        """ + PAGE_ARGUMENTS + """
        ,
            CAST(#{search.size} AS integer),
            CAST(#{search.offset} AS integer)
        )
    """)
    List<ReservationResponseDTO> selectAdminReservationPage(
            @Param("loginId") String loginId,
            @Param("search") ReservationSearchDTO search
    );

    @Select("""
        SELECT app_api.count_admin_reservations(
        """ + PAGE_ARGUMENTS + """
        )
    """)
    int countAdminReservationPage(
            @Param("loginId") String loginId,
            @Param("search") ReservationSearchDTO search
    );

    @Select("""
        SELECT *
        FROM app_api.get_admin_reservation_detail(
            CAST(#{loginId} AS varchar),
            CAST(#{reservationId} AS bigint)
        )
    """)
    ReservationResponseDTO selectAdminReservation(
            @Param("loginId") String loginId,
            @Param("reservationId") Long reservationId
    );

    @Select("""
        SELECT *
        FROM app_api.get_arrival_reservation_for_update(
            CAST(#{washSiteId} AS bigint),
            CAST(#{plateNumber} AS varchar),
            CAST(#{entryTime} AS timestamp)
        )
    """)
    ReservationResponseDTO selectArrivalReservationForUpdate(
            @Param("washSiteId") Long washSiteId,
            @Param("plateNumber") String plateNumber,
            @Param("entryTime") LocalDateTime entryTime
    );

    @Select("""
        SELECT *
        FROM app_api.get_reservation_for_arrival_check(
            CAST(#{washSiteId} AS bigint),
            CAST(#{plateNumber} AS varchar),
            CAST(#{arrivalTime} AS timestamp)
        )
    """)
    ReservationResponseDTO selectReservationForArrivalCheck(
            @Param("washSiteId") Long washSiteId,
            @Param("plateNumber") String plateNumber,
            @Param("arrivalTime") LocalDateTime arrivalTime
    );

    @Select("""
        SELECT app_api.get_available_reservation_device(
            CAST(#{washSiteId} AS bigint),
            CAST(#{reservationId} AS bigint),
            CAST(#{reservedDeviceId} AS bigint),
            CAST(#{arrivalTime} AS timestamp)
        )
    """)
    Long selectAvailableDeviceForArrival(
            @Param("washSiteId") Long washSiteId,
            @Param("reservationId") Long reservationId,
            @Param("reservedDeviceId") Long reservedDeviceId,
            @Param("arrivalTime") LocalDateTime arrivalTime
    );

    @Select("""
        SELECT app_api.record_reservation_arrival_deviation(
            CAST(#{reservationId} AS bigint),
            CAST(#{eventType} AS varchar),
            CAST(#{arrivalTime} AS timestamp)
        )
    """)
    int recordArrivalDeviation(
            @Param("reservationId") Long reservationId,
            @Param("eventType") String eventType,
            @Param("arrivalTime") LocalDateTime arrivalTime
    );

    @Select("""
        SELECT app_api.fulfill_reservation(
            CAST(#{reservationId} AS bigint)
        )
    """)
    int fulfillReservation(@Param("reservationId") Long reservationId);

    @Select("""
        SELECT app_api.mark_expired_reservations_no_show(
            CAST(#{noShowCutoff} AS timestamp)
        )
    """)
    int updateExpiredReservationsToNoShow(
            @Param("noShowCutoff") LocalDateTime noShowCutoff
    );
}
