package kdt.washPrj.customer;

import kdt.washPrj.customer.dto.CustomerMyPageDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerMapper {

    @Select("SELECT * FROM app_api.get_customer_my_page(#{loginId})")
    CustomerMyPageDTO findMyPageByLoginId(String loginId);

    @Select("""
        SELECT *
        FROM app_api.update_customer_my_page_and_get(
            #{loginId},
            #{request.name},
            #{request.birthDate},
            #{request.phone},
            #{request.gender},
            #{request.emailAddress}
        )
    """)
    CustomerMyPageDTO updateMyPageAndGet(
            @Param("loginId") String loginId,
            @Param("request") CustomerMyPageDTO request
    );

    @Select("""
        SELECT app_api.change_customer_password_checked(
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

    @Select("""
        SELECT app_api.withdraw_customer_checked(
            #{loginId},
            #{currentPassword}
        )
    """)
    int withdrawChecked(
            @Param("loginId") String loginId,
            @Param("currentPassword") String currentPassword
    );

    @Select("SELECT app_api.get_customer_status_by_vehicle(#{vehicleId})")
    String getCustomerStatusFromVehicle(Integer vehicleId);

    @Select("""
        SELECT status
          FROM customer
         WHERE customer_id = #{customerId}
           FOR UPDATE
    """)
    String findStatusForUpdate(
            @Param("customerId") Integer customerId
    );
}
