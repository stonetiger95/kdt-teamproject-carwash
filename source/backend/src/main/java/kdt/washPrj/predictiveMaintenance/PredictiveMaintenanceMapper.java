package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.SensorReadingDTO;
import kdt.washPrj.predictiveMaintenance.dto.AlertDTO;
import kdt.washPrj.predictiveMaintenance.dto.SimulationStatusDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PredictiveMaintenanceMapper {

    @Select("""
        SELECT app_api.get_pm_first_collected_at(
            CAST(#{loginId} AS varchar)
        )
    """)
    LocalDateTime findFirstCollectedAt(
            @Param("loginId") String loginId
    );

    @Select("""
        SELECT app_api.get_pm_latest_collected_at(
            CAST(#{loginId} AS varchar)
        )
    """)
    LocalDateTime findLatestCollectedAt(
            @Param("loginId") String loginId
    );

    @Select("SELECT app_api.get_pm_live_latest_collected_at(CAST(#{loginId} AS varchar))")
    LocalDateTime findLiveLatestCollectedAt(@Param("loginId") String loginId);

    @Select("SELECT * FROM app_api.get_pm_live_readings_at(CAST(#{loginId} AS varchar), CAST(#{collectedAt} AS timestamp))")
    List<SensorReadingDTO> findLiveReadingsAt(@Param("loginId") String loginId, @Param("collectedAt") LocalDateTime collectedAt);

    @Select("SELECT * FROM app_api.get_pm_demo_readings_at(CAST(#{loginId} AS varchar), CAST(#{collectedAt} AS timestamp))")
    List<SensorReadingDTO> findDemoReadingsAt(@Param("loginId") String loginId, @Param("collectedAt") LocalDateTime collectedAt);

    @Select("SELECT * FROM app_api.get_pm_demo_equipment_history(CAST(#{loginId} AS varchar), CAST(#{equipmentId} AS varchar), CAST(#{collectedAt} AS timestamp), CAST(#{limit} AS integer))")
    List<SensorReadingDTO> findDemoHistory(@Param("loginId") String loginId, @Param("equipmentId") String equipmentId,
                                           @Param("collectedAt") LocalDateTime collectedAt, @Param("limit") int limit);

    @Select("SELECT * FROM app_api.get_pm_live_equipment_history(CAST(#{loginId} AS varchar), CAST(#{equipmentId} AS varchar), CAST(#{collectedAt} AS timestamp), CAST(#{limit} AS integer))")
    List<SensorReadingDTO> findLiveHistory(@Param("loginId") String loginId, @Param("equipmentId") String equipmentId,
                                           @Param("collectedAt") LocalDateTime collectedAt, @Param("limit") int limit);

    @Select("SELECT * FROM app_api.get_pm_live_alerts(CAST(#{loginId} AS varchar), CAST(#{equipmentId} AS varchar))")
    List<AlertDTO> findLiveAlerts(@Param("loginId") String loginId, @Param("equipmentId") String equipmentId);

    @Select("SELECT * FROM app_api.start_pm_demo(CAST(#{loginId} AS varchar), CAST(#{telegramEnabled} AS boolean))")
    SimulationStatusDTO startDemo(@Param("loginId") String loginId, @Param("telegramEnabled") boolean telegramEnabled);

    @Select("SELECT * FROM app_api.stop_pm_demo(CAST(#{loginId} AS varchar))")
    SimulationStatusDTO stopDemo(@Param("loginId") String loginId);

    @Select("SELECT * FROM app_api.pause_pm_demo(CAST(#{loginId} AS varchar))")
    SimulationStatusDTO pauseDemo(@Param("loginId") String loginId);

    @Select("SELECT * FROM app_api.resume_pm_demo(CAST(#{loginId} AS varchar))")
    SimulationStatusDTO resumeDemo(@Param("loginId") String loginId);

    @Select("SELECT * FROM app_api.get_pm_demo_status(CAST(#{loginId} AS varchar))")
    SimulationStatusDTO getDemoStatus(@Param("loginId") String loginId);

    @Select("SELECT app_api.advance_pm_demo_runs()")
    int advanceDemoRuns();

    @Select("SELECT app_api.record_pm_demo_alert(CAST(#{loginId} AS varchar), CAST(#{demoRunId} AS bigint), CAST(#{equipmentId} AS varchar), CAST(#{alertLevel} AS varchar), CAST(#{riskScore} AS numeric), CAST(#{issueSummary} AS varchar), CAST(#{occurredAt} AS timestamp))")
    Long recordDemoAlert(@Param("loginId") String loginId, @Param("demoRunId") Long demoRunId,
                         @Param("equipmentId") String equipmentId, @Param("alertLevel") String alertLevel,
                         @Param("riskScore") Double riskScore, @Param("issueSummary") String issueSummary,
                         @Param("occurredAt") LocalDateTime occurredAt);

    @Select("SELECT * FROM app_api.get_pm_demo_alerts(CAST(#{loginId} AS varchar), CAST(#{demoRunId} AS bigint), CAST(#{equipmentId} AS varchar))")
    List<AlertDTO> findDemoAlerts(@Param("loginId") String loginId, @Param("demoRunId") Long demoRunId,
                                  @Param("equipmentId") String equipmentId);

    @Select("""
        SELECT app_api.get_pm_next_collected_at(
            CAST(#{loginId} AS varchar),
            CAST(#{currentTime} AS timestamp)
        )
    """)
    LocalDateTime findNextCollectedAt(
            @Param("loginId") String loginId,
            @Param("currentTime") LocalDateTime currentTime
    );

    @Select("""
        SELECT *
        FROM app_api.get_pm_readings_at(
            CAST(#{loginId} AS varchar),
            CAST(#{collectedAt} AS timestamp)
        )
    """)
    List<SensorReadingDTO> findReadingsAt(
            @Param("loginId") String loginId,
            @Param("collectedAt") LocalDateTime collectedAt
    );

    @Select("""
        SELECT *
        FROM app_api.get_pm_equipment_history(
            CAST(#{loginId} AS varchar),
            CAST(#{equipmentId} AS varchar),
            CAST(#{collectedAt} AS timestamp),
            CAST(#{limit} AS integer)
        )
    """)
    List<SensorReadingDTO> findReadingsByEquipmentUntil(
            @Param("loginId") String loginId,
            @Param("equipmentId") String equipmentId,
            @Param("collectedAt") LocalDateTime collectedAt,
            @Param("limit") int limit
    );

    @Select("""
        SELECT app_api.begin_pm_notification_delivery(
            CAST(#{loginId} AS varchar),
            CAST(#{eventKey} AS varchar),
            CAST(#{notificationType} AS varchar),
            CAST(#{equipmentId} AS varchar),
            CAST(#{recipient} AS varchar),
            CAST(#{messageBody} AS text),
            CAST(NULL AS bigint)
        )
    """)
    Long beginNotificationDelivery(
            @Param("loginId") String loginId,
            @Param("eventKey") String eventKey,
            @Param("notificationType") String notificationType,
            @Param("equipmentId") String equipmentId,
            @Param("recipient") String recipient,
            @Param("messageBody") String messageBody
    );

    @Select("""
        SELECT app_api.complete_pm_notification_delivery(
            CAST(#{loginId} AS varchar),
            CAST(#{deliveryId} AS bigint),
            CAST(#{success} AS boolean),
            CAST(#{errorMessage} AS varchar)
        )
    """)
    String completeNotificationDelivery(
            @Param("loginId") String loginId,
            @Param("deliveryId") Long deliveryId,
            @Param("success") boolean success,
            @Param("errorMessage") String errorMessage
    );

    @Select("""
        SELECT app_api.record_pm_shadow_prediction(
            CAST(#{loginId} AS varchar),
            CAST(#{sourceMode} AS varchar),
            CAST(#{equipmentId} AS varchar),
            CAST(#{collectedAt} AS timestamp),
            CAST(#{modelVersion} AS varchar),
            CAST(#{status} AS varchar),
            CAST(#{rawProbability} AS numeric),
            CAST(#{calibratedProbability} AS numeric),
            CAST(#{riskScore} AS numeric),
            CAST(#{riskLevel} AS varchar),
            CAST(#{historyRows} AS integer),
            CAST(#{message} AS varchar)
        )
    """)
    Long recordShadowPrediction(
            @Param("loginId") String loginId,
            @Param("sourceMode") String sourceMode,
            @Param("equipmentId") String equipmentId,
            @Param("collectedAt") LocalDateTime collectedAt,
            @Param("modelVersion") String modelVersion,
            @Param("status") String status,
            @Param("rawProbability") Double rawProbability,
            @Param("calibratedProbability") Double calibratedProbability,
            @Param("riskScore") Double riskScore,
            @Param("riskLevel") String riskLevel,
            @Param("historyRows") Integer historyRows,
            @Param("message") String message
    );
}
