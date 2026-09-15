package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.BeginPasswordRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.PasswordResetAuthorizationDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper
public interface PasswordRecoveryMapper {

    @Select("""
        SELECT *
        FROM app_api.begin_password_recovery(
            CAST(#{challengeId} AS uuid),
            CAST(#{loginId} AS varchar),
            CAST(#{emailAddress} AS varchar),
            CAST(#{method} AS varchar),
            CAST(#{subjectHmac} AS varchar),
            CAST(#{otpHmac} AS varchar),
            CAST(#{expiresAt} AS timestamptz),
            #{resendSeconds},
            #{maxAttempts}
        )
    """)
    BeginPasswordRecoveryResultDTO begin(
            @Param("challengeId") UUID challengeId,
            @Param("loginId") String loginId,
            @Param("emailAddress") String emailAddress,
            @Param("method") String method,
            @Param("subjectHmac") String subjectHmac,
            @Param("otpHmac") String otpHmac,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("resendSeconds") int resendSeconds,
            @Param("maxAttempts") int maxAttempts
    );

    @Select("""
        SELECT app_api.verify_email_password_recovery(
            CAST(#{challengeId} AS uuid),
            CAST(#{codeHmac} AS varchar),
            CAST(#{resetTokenHmac} AS varchar),
            CAST(#{resetExpiresAt} AS timestamptz)
        )
    """)
    String verifyEmail(
            @Param("challengeId") UUID challengeId,
            @Param("codeHmac") String codeHmac,
            @Param("resetTokenHmac") String resetTokenHmac,
            @Param("resetExpiresAt") OffsetDateTime resetExpiresAt
    );

    @Select("""
        SELECT app_api.mark_email_password_recovery_delivery(
            CAST(#{challengeId} AS uuid),
            CAST(#{deliveryStatus} AS varchar)
        )
    """)
    String markEmailDelivery(
            @Param("challengeId") UUID challengeId,
            @Param("deliveryStatus") String deliveryStatus
    );

    @Select("""
        SELECT app_api.complete_identity_password_recovery(
            CAST(#{challengeId} AS uuid),
            CAST(#{method} AS varchar),
            CAST(#{providerVerificationId} AS varchar),
            CAST(#{verificationMode} AS varchar),
            CAST(#{verifiedName} AS varchar),
            CAST(#{verifiedBirthDate} AS date),
            CAST(#{verifiedPhone} AS varchar),
            CAST(#{diHmac} AS varchar),
            CAST(#{ciHmac} AS varchar),
            CAST(#{providerVerifiedAt} AS timestamptz),
            CAST(#{resetTokenHmac} AS varchar),
            CAST(#{resetExpiresAt} AS timestamptz)
        )
    """)
    String completeIdentity(
            @Param("challengeId") UUID challengeId,
            @Param("method") String method,
            @Param("providerVerificationId") String providerVerificationId,
            @Param("verificationMode") String verificationMode,
            @Param("verifiedName") String verifiedName,
            @Param("verifiedBirthDate") LocalDate verifiedBirthDate,
            @Param("verifiedPhone") String verifiedPhone,
            @Param("diHmac") String diHmac,
            @Param("ciHmac") String ciHmac,
            @Param("providerVerifiedAt") OffsetDateTime providerVerifiedAt,
            @Param("resetTokenHmac") String resetTokenHmac,
            @Param("resetExpiresAt") OffsetDateTime resetExpiresAt
    );

    @Select("""
        SELECT *
        FROM app_api.password_reset_authorization_status(
            CAST(#{resetTokenHmac} AS varchar)
        )
    """)
    PasswordResetAuthorizationDTO authorizationStatus(
            @Param("resetTokenHmac") String resetTokenHmac
    );

    @Select("""
        SELECT app_api.consume_password_reset_authorization(
            CAST(#{resetTokenHmac} AS varchar),
            CAST(#{newPassword} AS varchar)
        )
    """)
    String consume(
            @Param("resetTokenHmac") String resetTokenHmac,
            @Param("newPassword") String newPassword
    );

    @Select("SELECT app_api.cleanup_password_recovery(#{retentionHours})")
    int cleanup(@Param("retentionHours") int retentionHours);
}
