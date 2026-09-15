package kdt.washPrj.gate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GateMapper {

    @Select("SELECT app_api.get_active_device_type(#{deviceId})")
    String selectDeviceTypeById(@Param("deviceId") Integer deviceId);

    @Select("""
    SELECT app_api.get_active_device_wash_site_id(
        CAST(#{deviceId} AS bigint)
    )
    """)
    Long selectWashSiteIdByDeviceId(
            @Param("deviceId") Integer deviceId
    );

    @Select("""
    SELECT app_api.create_entry_exit(
        CAST(#{entryExit.plateNumber} AS varchar),
        CAST(#{entryExit.vehicleId} AS bigint),
        CAST(#{entryExit.customerId} AS bigint),
        CAST(#{entryExit.entryDetectionLogId} AS bigint),
        CAST(#{deviceId} AS bigint)
    )
    """)
    Integer insertEntry(
            @Param("entryExit") EntryExitVO entryExit,
            @Param("deviceId") Integer deviceId
    );

    @Select("SELECT * FROM app_api.get_latest_open_entry(#{plateNumber}, #{deviceId})")
    EntryExitVO findLatestOpenEntryByPlateNumber(
            @Param("plateNumber") String plateNumber,
            @Param("deviceId") Integer deviceId
    );

    @Select("SELECT app_api.mark_entry_exited(#{entryExitId}, #{exitDetectionLogId})")
    int updateEntryExitExit(
            @Param("entryExitId") Integer entryExitId,
            @Param("exitDetectionLogId") Integer exitDetectionLogId
    );

    @Select("SELECT app_api.get_open_wash_history_id(#{entryExitId})")
    Integer findOpenWashHistoryIdByEntryExitId(@Param("entryExitId") Integer entryExitId);

    @Select("SELECT app_api.get_wash_payment_status(#{washHistoryId})")
    String selectPaymentStatus(@Param("washHistoryId") Integer washHistoryId);

    @Select("SELECT app_api.mark_wash_exited(#{washHistoryId})")
    int updateWashExit(@Param("washHistoryId") Integer washHistoryId);

    @Select("""
        SELECT app_api.record_gate_event(
            #{entryExitId},
            #{washHistoryId},
            #{deviceId},
            #{eventType},
            #{reason}
        )
    """)
    int insertGateEvent(
            @Param("entryExitId") Integer entryExitId,
            @Param("washHistoryId") Integer washHistoryId,
            @Param("deviceId") Integer deviceId,
            @Param("eventType") String eventType,
            @Param("reason") String reason
    );

    @Select("SELECT app_api.get_wash_status(#{washHistoryId})")
    String selectWashStatus(@Param("washHistoryId") Integer washHistoryId);
}
