package kdt.washPrj.predictiveMaintenance;

import kdt.washPrj.predictiveMaintenance.dto.PredictiveMaintenanceLogRowDTO;
import kdt.washPrj.predictiveMaintenance.dto.V2ShadowPredictionResultDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only mapper. All table access remains behind administrator app_api functions. */
@Mapper
public interface PredictiveMaintenanceLogMapper {

    @Select("""
        SELECT app_api.get_pm_sensor_log_count(
            CAST(#{loginId} AS varchar), CAST(#{mode} AS varchar),
            CAST(#{equipmentId} AS varchar), CAST(#{fromAt} AS timestamp),
            CAST(#{toAt} AS timestamp), CAST(#{riskLevel} AS varchar)
        )
    """)
    long countLogs(
            @Param("loginId") String loginId,
            @Param("mode") String mode,
            @Param("equipmentId") String equipmentId,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            @Param("riskLevel") String riskLevel
    );

    @Select("""
        SELECT *
          FROM app_api.get_pm_sensor_logs(
            CAST(#{loginId} AS varchar), CAST(#{mode} AS varchar),
            CAST(#{equipmentId} AS varchar), CAST(#{fromAt} AS timestamp),
            CAST(#{toAt} AS timestamp), CAST(#{riskLevel} AS varchar),
            CAST(#{offset} AS integer), CAST(#{limit} AS integer)
          )
    """)
    List<PredictiveMaintenanceLogRowDTO> findLogs(
            @Param("loginId") String loginId,
            @Param("mode") String mode,
            @Param("equipmentId") String equipmentId,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            @Param("riskLevel") String riskLevel,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    @Select("""
        SELECT *
          FROM app_api.get_pm_shadow_prediction(
            CAST(#{loginId} AS varchar), CAST(#{mode} AS varchar),
            CAST(#{equipmentId} AS varchar),
            CAST(#{collectedAt} AS timestamp)
          )
    """)
    V2ShadowPredictionResultDTO findShadowPrediction(
            @Param("loginId") String loginId,
            @Param("mode") String mode,
            @Param("equipmentId") String equipmentId,
            @Param("collectedAt") LocalDateTime collectedAt
    );
}
