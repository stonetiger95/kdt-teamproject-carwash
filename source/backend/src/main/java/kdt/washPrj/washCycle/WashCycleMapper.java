package kdt.washPrj.washCycle;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WashCycleMapper {
    @Select("""
    SELECT app_api.create_wash_cycle_from_entry(
        CAST(#{entryExitId} AS bigint),
        CAST(#{washSiteId} AS bigint),
        CAST(#{deviceId} AS bigint),
        CAST(#{courseId} AS bigint),
        CAST(#{customerType} AS varchar),
        CAST(#{paymentStatus} AS varchar),
        CAST(#{reservationId} AS bigint),
        CAST(#{washPrice} AS integer)
    )
    """)
    Integer createWashHistoryFromEntry(
            @Param("entryExitId") Integer entryExitId,
            @Param("washSiteId") Integer washSiteId,
            @Param("deviceId") Integer deviceId,
            @Param("courseId") Integer courseId,
            @Param("customerType") String customerType,
            @Param("paymentStatus") String paymentStatus,
            @Param("reservationId") Long reservationId,
            @Param("washPrice") Integer washPrice
    );

    @Select("SELECT app_api.get_active_wash_site_id(CAST(#{deviceId} AS bigint))")
    Integer selectWashSiteIdByDeviceId(@Param("deviceId") Integer deviceId);

    @Select("""
    SELECT  course_id AS courseId,
            wash_price AS washPrice
      FROM  app_api.get_default_walk_in_course(
                CAST(#{washSiteId} AS bigint)
            )
    """)
    WalkInCourseDTO selectDefaultWalkInCourse(
            @Param("washSiteId") Integer washSiteId
    );

    @Select("""
    SELECT  wash_history_id AS washHistoryId,
            reservation_id AS reservationId,
            device_id AS deviceId,
            reserved_device_id AS reservedDeviceId,
            reservation_status AS reservationStatus,
            customer_id AS customerId,
            vehicle_id AS vehicleId,
            wash_site_id AS washSiteId,
            course_id AS courseId,
            customer_type AS customerType,
            payment_status AS paymentStatus,
            wash_status AS washStatus
      FROM  app_api.get_wash_cycle_state_with_reservation(
                CAST(#{washHistoryId} AS bigint)
            )
    """)
    WashCycleStateDTO selectWashCycleState(
            @Param("washHistoryId") Integer washHistoryId
    );

    @Select("""
    SELECT app_api.start_wash_cycle_if_allowed(
        CAST(#{washHistoryId} AS bigint)
    )
    """)
    int startWashIfAllowed(
            @Param("washHistoryId") Integer washHistoryId
    );
}
