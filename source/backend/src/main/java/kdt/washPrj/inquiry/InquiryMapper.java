package kdt.washPrj.inquiry;

import kdt.washPrj.inquiry.dto.InquiryRequestDTO;
import kdt.washPrj.inquiry.dto.InquiryResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryUpdateDTO;
import kdt.washPrj.inquiry.dto.InquiryWashSiteDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InquiryMapper {

    @Select("""
        SELECT app_api.create_inquiry_checked(
            CAST(#{loginId} AS varchar),
            CAST(#{request.title} AS varchar),
            CAST(#{request.content} AS text),
            CAST(#{request.paymentHistoryId} AS bigint),
            CAST(#{request.washSiteId} AS bigint)
        )
    """)
    int insertInquiry(
            @Param("loginId") String loginId,
            @Param("request") InquiryRequestDTO requestDTO
    );

    @Select("SELECT * FROM app_api.get_inquiry_list(CAST(#{loginId} AS varchar))")
    List<InquiryResponseDTO> selectAuthorizedInquiryList(@Param("loginId") String loginId);

    @Select("""
        SELECT *
        FROM app_api.get_inquiry_detail_bundle(
            CAST(#{loginId} AS varchar),
            CAST(#{inquiryId} AS bigint)
        )
    """)
    InquiryResponseDTO selectAuthorizedInquiryById(
            @Param("loginId") String loginId,
            @Param("inquiryId") Long inquiryId
    );

    @Select("""
        SELECT app_api.update_my_inquiry_checked(
            CAST(#{loginId} AS varchar),
            CAST(#{inquiryId} AS bigint),
            CAST(#{request.title} AS varchar),
            CAST(#{request.content} AS text),
            CAST(#{request.paymentHistoryId} AS bigint),
            CAST(#{request.washSiteId} AS bigint)
        )
    """)
    int updateInquiry(
            @Param("loginId") String loginId,
            @Param("inquiryId") Long inquiryId,
            @Param("request") InquiryUpdateDTO requestDTO
    );

    @Select("SELECT * FROM app_api.get_customer_used_wash_sites_with_contact(CAST(#{loginId} AS varchar))")
    List<InquiryWashSiteDTO> selectCustomerUsedWashSites(@Param("loginId") String loginId);

    @Select("""
        SELECT app_api.append_inquiry_answer(
            CAST(#{loginId} AS varchar),
            CAST(#{inquiryId} AS bigint),
            CAST(#{answer} AS text)
        )
    """)
    int appendInquiryAnswer(
            @Param("loginId") String loginId,
            @Param("inquiryId") Long inquiryId,
            @Param("answer") String answer
    );

    @Select("""
        SELECT app_api.delete_my_inquiry(
            CAST(#{loginId} AS varchar),
            CAST(#{inquiryId} AS bigint)
        )
    """)
    int deleteInquiry(
            @Param("loginId") String loginId,
            @Param("inquiryId") Long inquiryId
    );
}
