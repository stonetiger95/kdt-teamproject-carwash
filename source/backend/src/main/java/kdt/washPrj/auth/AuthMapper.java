package kdt.washPrj.auth;

import kdt.washPrj.auth.dto.AuthRegistrationResultDTO;
import kdt.washPrj.auth.dto.LoginRequestDTO;
import kdt.washPrj.auth.dto.SocialSignupRequestDTO;
import kdt.washPrj.auth.dto.OwnerRegisterRequestDTO;
import kdt.washPrj.auth.dto.SignupRequestDTO;
import kdt.washPrj.auth.identity.VerifiedIdentityClaims;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthMapper {

    @Select("SELECT * FROM app_api.authenticate_account(#{loginId}, #{password})")
    AccountVO login(LoginRequestDTO request);

    @Select("SELECT app_api.rotate_account_login_version(#{loginId}, #{role})")
    Long rotateAccountLoginVersion(
            @Param("loginId") String loginId,
            @Param("role") String role
    );

    @Select("SELECT app_api.account_login_id_exists(#{loginId})")
    int existsLoginId(String loginId);

    @Select("SELECT app_api.contact_phone_exists(#{phone})")
    int existsPhone(String phone);

    @Select("SELECT app_api.contact_email_exists(#{emailAddress})")
    int existsEmail(String emailAddress);

    @Select("SELECT app_api.owner_business_number_exists(#{businessNumber})")
    int existsBusinessNumber(String businessNumber);

    @Select("""
        SELECT *
        FROM app_api.register_identity_verified_customer_account_checked(
            CAST(#{request.loginId} AS varchar),
            CAST(#{request.password} AS varchar),
            CAST(#{request.emailAddress} AS varchar),
            CAST(#{provider} AS varchar),
            CAST(#{identity.verificationMode} AS varchar),
            CAST(#{identity.identityVerificationId} AS varchar),
            CAST(#{identity.name} AS varchar),
            CAST(#{identity.birthDate} AS date),
            CAST(#{identity.phone} AS varchar),
            CAST(#{identity.gender} AS varchar),
            CAST(#{request.gender} AS varchar),
            CAST(#{identity.diHmac} AS varchar),
            CAST(#{identity.verifiedAt} AS timestamptz),
            CAST(#{identity.proofExpiresAt} AS timestamptz)
        )
    """)
    AuthRegistrationResultDTO registerIdentityVerifiedCustomerChecked(
            @Param("request") SignupRequestDTO request,
            @Param("provider") String provider,
            @Param("identity") VerifiedIdentityClaims identity
    );

    @Select("""
        SELECT *
        FROM app_api.register_customer_account_checked(
            #{request.loginId},
            #{password},
            #{request.userName},
            #{request.birthDate},
            #{request.phone},
            #{request.gender},
            #{request.emailAddress}
        )
    """)
    AuthRegistrationResultDTO registerSocialCustomerChecked(
            @Param("request") SocialSignupRequestDTO request,
            @Param("password") String password
    );

    @Select("""
        SELECT *
        FROM app_api.register_owner_account_checked(
            #{adminLoginId},
            #{request.loginId},
            #{request.password},
            #{request.ownerName},
            #{request.businessNumber},
            #{request.phone},
            #{request.emailAddress},
            #{request.businessAddress}
        )
    """)
    AuthRegistrationResultDTO registerOwnerChecked(
            @Param("adminLoginId") String adminLoginId,
            @Param("request") OwnerRegisterRequestDTO request
    );

    @Select("SELECT * FROM app_api.resolve_social_login_account(#{provider}, #{providerUserId})")
    AccountVO resolveSocialLoginAccount(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId
    );

    @Select("SELECT app_api.link_social_account(#{provider}, #{providerUserId}, #{loginId})")
    String linkSocialAccount(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId,
            @Param("loginId") String loginId
    );

    @Select("SELECT app_api.unlink_social_account(#{loginId}, #{provider})")
    int unlinkSocialAccount(
            @Param("loginId") String loginId,
            @Param("provider") String provider
    );
}
