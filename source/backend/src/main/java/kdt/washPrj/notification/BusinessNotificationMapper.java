package kdt.washPrj.notification;

import kdt.washPrj.notification.dto.BusinessNotificationEventDTO;
import kdt.washPrj.notification.dto.BusinessNotificationTargetDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BusinessNotificationMapper {

    @Select("""
        SELECT *
        FROM app_api.claim_business_notification_events(
            CAST(#{limit} AS integer),
            CAST(#{leaseSeconds} AS integer)
        )
    """)
    List<BusinessNotificationEventDTO> claimEvents(
            @Param("limit") int limit,
            @Param("leaseSeconds") int leaseSeconds
    );

    @Select("""
        SELECT *
        FROM app_api.get_business_notification_targets(
            CAST(#{eventType} AS varchar),
            CAST(#{entityId} AS bigint)
        )
    """)
    List<BusinessNotificationTargetDTO> selectTargets(
            @Param("eventType") String eventType,
            @Param("entityId") Long entityId
    );

    @Select("""
        SELECT app_api.begin_business_notification_delivery(
            CAST(#{eventId} AS bigint),
            CAST(#{channel} AS varchar),
            CAST(#{recipient} AS varchar),
            CAST(#{targetLoginId} AS varchar),
            CAST(#{targetRole} AS varchar),
            CAST(#{message} AS text)
        )
    """)
    Long beginDelivery(
            @Param("eventId") Long eventId,
            @Param("channel") String channel,
            @Param("recipient") String recipient,
            @Param("targetLoginId") String targetLoginId,
            @Param("targetRole") String targetRole,
            @Param("message") String message
    );

    @Select("""
        SELECT app_api.complete_business_notification_delivery(
            CAST(#{deliveryId} AS bigint),
            CAST(#{status} AS varchar),
            CAST(#{errorMessage} AS varchar)
        )
    """)
    int completeDelivery(
            @Param("deliveryId") Long deliveryId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage
    );

    @Select("""
        SELECT app_api.complete_business_notification_event(
            CAST(#{eventId} AS bigint),
            CAST(#{status} AS varchar),
            CAST(#{errorMessage} AS varchar),
            CAST(#{retrySeconds} AS integer)
        )
    """)
    int completeEvent(
            @Param("eventId") Long eventId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("retrySeconds") int retrySeconds
    );

    @Select("""
        SELECT app_api.is_line_notification_linked(
            CAST(#{loginId} AS varchar)
        )
    """)
    boolean isLineLinked(@Param("loginId") String loginId);
}
