package kdt.washPrj.settlement;

import kdt.washPrj.settlement.dto.SettlementSiteComparisonDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SettlementMapper {

    @Select("""
        SELECT app_api.get_settlement_dashboard_bundle(
            CAST(#{loginId} AS varchar),
            CAST(#{ownerId} AS bigint),
            CAST(#{washSiteId} AS bigint),
            CAST(#{periodType} AS varchar),
            CAST(#{startDate} AS date),
            CAST(#{endDate} AS date)
        )
    """)
    String selectDashboardBundle(
            @Param("loginId") String loginId,
            @Param("ownerId") Long ownerId,
            @Param("washSiteId") Long washSiteId,
            @Param("periodType") String periodType,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    @Select("""
        SELECT *
        FROM app_api.get_settlement_site_comparisons(
            CAST(#{loginId} AS varchar),
            CAST(#{ownerId} AS bigint),
            CAST(#{washSiteId} AS bigint),
            CAST(#{startDate} AS date),
            CAST(#{endDate} AS date)
        )
    """)
    List<SettlementSiteComparisonDTO> selectSiteComparisons(
            @Param("loginId") String loginId,
            @Param("ownerId") Long ownerId,
            @Param("washSiteId") Long washSiteId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
