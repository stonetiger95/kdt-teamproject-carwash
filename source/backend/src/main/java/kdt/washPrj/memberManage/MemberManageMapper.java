package kdt.washPrj.memberManage;

import kdt.washPrj.memberManage.dto.MemberManageDetailDTO;
import kdt.washPrj.memberManage.dto.MemberManageListDTO;
import kdt.washPrj.memberManage.dto.MemberManageSearchDTO;
import kdt.washPrj.memberManage.dto.MemberUpdateDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberManageMapper {

    @Select("""
        SELECT *
        FROM app_api.get_admin_member_page(
            #{loginId},
            #{search.keyword},
            #{search.status},
            #{search.customerType},
            #{search.size},
            #{search.offset}
        )
    """)
    List<MemberManageListDTO> getMemberList(
            @Param("loginId") String loginId,
            @Param("search") MemberManageSearchDTO searchDTO
    );

    @Select("SELECT * FROM app_api.get_admin_member_detail_bundle(#{loginId}, #{customerId})")
    MemberManageDetailDTO getMemberDetail(
            @Param("loginId") String loginId,
            @Param("customerId") Integer customerId
    );

    @Select("""
        SELECT app_api.update_admin_member_with_blacklist(
            #{loginId},
            #{customerId},
            #{request.name},
            #{request.birthDate},
            #{request.phone},
            #{request.status},
            #{request.gender},
            #{request.emailAddress}
        )
    """)
    int updateMemberDetail(
            @Param("loginId") String loginId,
            @Param("customerId") Integer customerId,
            @Param("request") MemberUpdateDTO request
    );
}
