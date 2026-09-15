package kdt.washPrj.owner;

import kdt.washPrj.owner.dto.OwnerMyPageDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OwnerMapper {

    @Select("SELECT * FROM app_api.get_owner_my_page(#{loginId})")
    OwnerMyPageDTO findMyPageByLoginId(String loginId);

    @Select("""
        SELECT *
        FROM app_api.update_owner_my_page_and_get(
            #{loginId},
            #{request.ownerName},
            #{request.businessNumber},
            #{request.phone},
            #{request.emailAddress},
            #{request.businessAddress}
        )
    """)
    OwnerMyPageDTO updateMyPageAndGet(
            @Param("loginId") String loginId,
            @Param("request") OwnerMyPageDTO request
    );

    @Select("""
        SELECT app_api.change_owner_password_checked(
            #{loginId},
            #{currentPassword},
            #{newPassword}
        )
    """)
    int changePasswordChecked(
            @Param("loginId") String loginId,
            @Param("currentPassword") String currentPassword,
            @Param("newPassword") String newPassword
    );
}
