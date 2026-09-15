package kdt.washPrj.businessHour;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BusinessHourMapper {

    @Select("""
        SELECT app_api.owner_owns_wash_site(
            #{loginId},
            #{washSiteId}
        )
    """)
    boolean existsOwnerWashSite(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT *
        FROM app_api.get_wash_site_business_hours(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    List<BusinessHourDTO> selectBusinessHours(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT app_api.upsert_wash_site_business_hour(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint),
            CAST(#{businessHour.dayOfWeek} AS integer),
            CAST(#{businessHour.openTime} AS time),
            CAST(#{businessHour.closeTime} AS time),
            CAST(#{businessHour.closed} AS boolean),
            CAST(#{businessHour.twentyFourHours} AS boolean)
        )
    """)
    int upsertBusinessHour(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId,
            @Param("businessHour") BusinessHourDTO businessHour
    );

    @Select("""
        SELECT app_api.ensure_default_wash_site_business_hours(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    int insertDefaultBusinessHours(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );
}
