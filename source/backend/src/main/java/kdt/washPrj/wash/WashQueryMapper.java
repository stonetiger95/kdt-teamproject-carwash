package kdt.washPrj.wash;

import kdt.washPrj.wash.dto.WashHistorySearchDTO;
import kdt.washPrj.wash.dto.WashResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WashQueryMapper {

    @Select("""
        SELECT *
        FROM app_api.get_wash_history_list(
            CAST(#{loginId} AS varchar),
            CAST(#{search.ownerId} AS bigint),
            CAST(#{search.washSiteId} AS bigint),
            CAST(#{search.plateNumber} AS varchar),
            CAST(#{search.customerType} AS varchar),
            CAST(#{search.washStatus} AS varchar),
            CAST(#{search.paymentStatus} AS varchar),
            CAST(#{search.startDate} AS date),
            CAST(#{search.endDate} AS date),
            CAST(#{limit} AS integer),
            CAST(#{offset} AS integer)
        )
    """)
    List<WashResponseDTO> selectAuthorizedList(
            @Param("loginId") String loginId,
            @Param("search") WashHistorySearchDTO search,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset
    );

    @Select("""
        SELECT *
        FROM app_api.get_wash_history_page(
            CAST(#{loginId} AS varchar),
            CAST(#{search.ownerId} AS bigint),
            CAST(#{search.washSiteId} AS bigint),
            CAST(#{search.plateNumber} AS varchar),
            CAST(#{search.customerType} AS varchar),
            CAST(#{search.washStatus} AS varchar),
            CAST(#{search.paymentStatus} AS varchar),
            CAST(#{search.startDate} AS date),
            CAST(#{search.endDate} AS date),
            CAST(#{search.size} AS integer),
            CAST(#{search.offset} AS integer)
        )
    """)
    List<WashResponseDTO> selectAuthorizedPage(
            @Param("loginId") String loginId,
            @Param("search") WashHistorySearchDTO search
    );

    @Select("""
        SELECT *
        FROM app_api.get_wash_history_detail(
            CAST(#{loginId} AS varchar),
            CAST(#{washHistoryId} AS bigint)
        )
    """)
    WashResponseDTO selectAuthorizedById(
            @Param("loginId") String loginId,
            @Param("washHistoryId") Long washHistoryId
    );

    @Select("""
        SELECT app_api.get_internal_wash_history_image_path(
            CAST(#{washHistoryId} AS bigint),
            CAST(#{imageType} AS varchar)
        )
    """)
    String selectImagePath(
            @Param("washHistoryId") Long washHistoryId,
            @Param("imageType") String imageType
    );

    @Select("""
        SELECT *
        FROM app_api.get_public_non_member_pending(
            CAST(#{plateLast4} AS varchar)
        )
    """)
    List<WashResponseDTO> selectNonMemberPending(
            @Param("plateLast4") String plateLast4
    );

    @Select("""
        SELECT *
        FROM app_api.get_public_non_member_history(
            CAST(#{plateNumber} AS varchar)
        )
    """)
    List<WashResponseDTO> selectNonMemberHistory(
            @Param("plateNumber") String plateNumber
    );
}
