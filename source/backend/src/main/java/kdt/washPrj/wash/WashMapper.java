package kdt.washPrj.wash;

import kdt.washPrj.wash.dto.WashResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WashMapper {

    @Select("""
        SELECT *
        FROM app_api.get_internal_wash_history(
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    WashResponseDTO selectWashHistoryById(@Param("washHistoryId") Long washHistoryId);

    @Select("""
        SELECT app_api.start_owner_wash(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    int updateOwnerWashStart(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT app_api.end_owner_wash(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    int updateOwnerWashEnd(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT app_api.exit_owner_wash(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    int updateOwnerExit(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT app_api.update_owner_wash_payment_status(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{paymentStatus} AS varchar)
        )
    """)
    int updateOwnerPaymentStatus(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("paymentStatus") String paymentStatus
    );

    @Select("""
        SELECT app_api.update_owner_wash_status(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint),
            CAST(#{washStatus} AS varchar)
        )
    """)
    int updateOwnerWashStatus(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId,
            @Param("washStatus") String washStatus
    );

    @Select("""
        SELECT app_api.start_wash(
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    int updateWashStart(@Param("washHistoryId") Long washHistoryId);

    @Select("""
        SELECT app_api.end_wash(
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    int updateWashEnd(@Param("washHistoryId") Long washHistoryId);

    @Select("""
        SELECT app_api.exit_wash(
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    int updateExit(@Param("washHistoryId") Long washHistoryId);

    @Select("""
        SELECT app_api.update_wash_payment_status(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{paymentStatus} AS varchar)
        )
    """)
    int updatePaymentStatus(
            @Param("washHistoryId") Long washHistoryId,
            @Param("paymentStatus") String paymentStatus
    );

    @Select("""
        SELECT app_api.update_wash_status(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{washStatus} AS varchar)
        )
    """)
    int updateWashStatus(
            @Param("washHistoryId") Long washHistoryId,
            @Param("washStatus") String washStatus
    );
}
