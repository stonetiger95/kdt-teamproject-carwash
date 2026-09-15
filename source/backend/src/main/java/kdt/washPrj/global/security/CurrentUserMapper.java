package kdt.washPrj.global.security;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CurrentUserMapper {

    @Select("SELECT app_api.get_actor_pk(#{loginId}, #{role})")
    Integer findActorPk(
            @Param("loginId") String loginId,
            @Param("role") String role
    );

    @Select("SELECT app_api.is_allowed_customer(#{loginId})")
    int countAllowCustomerByLoginId(@Param("loginId") String loginId);

    @Select("SELECT app_api.is_current_credential(#{loginId}, #{role}, #{credentialVersion})")
    int isCurrentCredential(
            @Param("loginId") String loginId,
            @Param("role") String role,
            @Param("credentialVersion") long credentialVersion
    );
}
