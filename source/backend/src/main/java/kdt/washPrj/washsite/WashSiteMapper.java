package kdt.washPrj.washsite;

import kdt.washPrj.washsite.dto.OwnerResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import kdt.washPrj.washsite.dto.WashSiteRequestDTO;
import kdt.washPrj.washsite.dto.WashSiteResponseDTO;
import kdt.washPrj.washsite.dto.WashSiteSaveResultDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WashSiteMapper {

    @Select("""
        SELECT *
        FROM app_api.get_owner_list_with_address(
            CAST(#{loginId} AS varchar)
        )
    """)
    List<OwnerResponseDTO> selectOwnerList(@Param("loginId") String loginId);

    @Select("""
        SELECT *
        FROM app_api.get_wash_site_list(
            CAST(#{loginId} AS varchar),
            CAST(NULL AS bigint)
        )
    """)
    List<WashSiteResponseDTO> selectAdminWashSiteList(@Param("loginId") String loginId);

    @Select("""
        SELECT *
        FROM app_api.get_wash_site_list(
            CAST(#{loginId} AS varchar),
            CAST(#{ownerId} AS bigint)
        )
    """)
    List<WashSiteResponseDTO> selectAdminWashSiteListByOwnerId(
            @Param("loginId") String loginId,
            @Param("ownerId") Long ownerId
    );

    @Select("""
        SELECT *
        FROM app_api.get_wash_site_detail(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    WashSiteResponseDTO selectAdminWashSite(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT *
        FROM app_api.get_wash_site_list(
            CAST(#{loginId} AS varchar),
            CAST(NULL AS bigint)
        )
    """)
    List<WashSiteResponseDTO> selectOwnerWashSiteList(@Param("loginId") String loginId);

    @Select("""
        SELECT *
        FROM app_api.get_wash_site_detail(
            CAST(#{loginId} AS varchar),
            CAST(#{washSiteId} AS bigint)
        )
    """)
    WashSiteResponseDTO selectOwnerWashSite(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        WITH saved AS MATERIALIZED (
            SELECT app_api.save_wash_site(
                CAST(#{loginId} AS varchar),
                CAST(#{washSiteId} AS bigint),
                CAST(#{requestDTO.ownerId} AS bigint),
                CAST(#{requestDTO.siteName} AS varchar),
                CAST(#{requestDTO.address} AS varchar),
                CAST(#{requestDTO.sitePhone} AS varchar),
                CAST(#{requestDTO.status} AS varchar),
                CAST(
                    COALESCE(
                        NULLIF(BTRIM(CAST(#{requestDTO.courseName} AS varchar)), ''),
                        '기본 세차'
                    ) AS varchar
                ),
                CAST(#{requestDTO.washPrice} AS integer),
                CAST(#{requestDTO.latitude} AS double precision),
                CAST(#{requestDTO.longitude} AS double precision)
            ) AS affected_count
        )
        SELECT
            CASE
                WHEN affected_count = 0 THEN NULL
                WHEN #{washSiteId} IS NOT NULL THEN CAST(#{washSiteId} AS bigint)
                ELSE CURRVAL(
                    PG_GET_SERIAL_SEQUENCE('public.wash_site', 'wash_site_id')
                )
            END AS wash_site_id,
            affected_count
        FROM saved
    """)
    WashSiteSaveResultDTO saveWashSite(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId,
            @Param("requestDTO") WashSiteRequestDTO requestDTO
    );

    // 고객 예약 가능 세차장 조회
    @Select("""
        SELECT *
        FROM app_api.get_customer_reservation_wash_sites()
    """)
    List<WashSiteResponseDTO> selectCustomerWashSiteList();

    @Select("""
        SELECT *
        FROM app_api.get_public_map_wash_sites(
            CAST(#{keyword} AS VARCHAR),
            CAST(#{south} AS DOUBLE PRECISION),
            CAST(#{north} AS DOUBLE PRECISION),
            CAST(#{west} AS DOUBLE PRECISION),
            CAST(#{east} AS DOUBLE PRECISION)
        )
    """)
    List<WashSiteMapDTO> selectMapWashSites(
            @Param("keyword") String keyword,
            @Param("south") Double south,
            @Param("north") Double north,
            @Param("west") Double west,
            @Param("east") Double east
    );

    @Select("""
        SELECT *
        FROM app_api.get_public_map_wash_site(CAST(#{washSiteId} AS BIGINT))
    """)
    WashSiteMapDTO selectMapWashSite(@Param("washSiteId") Long washSiteId);

    @Select("""
        SELECT wash_site_id
        FROM app_api.get_customer_wash_site_favorite_ids(
            CAST(#{loginId} AS VARCHAR)
        )
    """)
    List<Long> selectCustomerFavoriteWashSiteIds(@Param("loginId") String loginId);

    @Select("""
        SELECT app_api.add_customer_wash_site_favorite(
            CAST(#{loginId} AS VARCHAR),
            CAST(#{washSiteId} AS BIGINT)
        )
    """)
    int insertCustomerWashSiteFavorite(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );

    @Select("""
        SELECT app_api.remove_customer_wash_site_favorite(
            CAST(#{loginId} AS VARCHAR),
            CAST(#{washSiteId} AS BIGINT)
        )
    """)
    int deleteCustomerWashSiteFavorite(
            @Param("loginId") String loginId,
            @Param("washSiteId") Long washSiteId
    );
}
