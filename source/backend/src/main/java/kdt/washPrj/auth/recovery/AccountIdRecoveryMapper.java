package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.AccountIdRecoveryResultDTO;
import kdt.washPrj.auth.recovery.dto.BeginPasswordRecoveryResultDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper
public interface AccountIdRecoveryMapper {

    @Select("""
        SELECT *
        FROM app_api.begin_account_id_recovery_email(
            CAST(#{challengeId} AS uuid),
            CAST(#{emailAddress} AS varchar),
            CAST(#{subjectHmac} AS varchar),
            CAST(#{otpHmac} AS varchar),
            CAST(#{expiresAt} AS timestamptz),
            #{resendSeconds},
            #{maxAttempts}
        )
    """)
    BeginPasswordRecoveryResultDTO beginEmail(
            @Param("challengeId") UUID challengeId,
            @Param("emailAddress") String emailAddress,
            @Param("subjectHmac") String subjectHmac,
            @Param("otpHmac") String otpHmac,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("resendSeconds") int resendSeconds,
            @Param("maxAttempts") int maxAttempts
    );

    @Select("""
        SELECT *
        FROM app_api.begin_account_id_recovery_identity(
            CAST(#{challengeId} AS uuid),
            CAST(#{method} AS varchar),
            CAST(#{subjectHmac} AS varchar),
            CAST(#{expiresAt} AS timestamptz),
            #{resendSeconds},
            #{maxAttempts}
        )
    """)
    BeginPasswordRecoveryResultDTO beginIdentity(
            @Param("challengeId") UUID challengeId,
            @Param("method") String method,
            @Param("subjectHmac") String subjectHmac,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("resendSeconds") int resendSeconds,
            @Param("maxAttempts") int maxAttempts
    );

    @Select("""
        SELECT app_api.mark_account_id_recovery_delivery(
            CAST(#{challengeId} AS uuid),
            CAST(#{deliveryStatus} AS varchar)
        )
    """)
    int markEmailDelivery(
            @Param("challengeId") UUID challengeId,
            @Param("deliveryStatus") String deliveryStatus
    );

    @Select("""
        SELECT *
        FROM app_api.verify_email_account_id_recovery(
            CAST(#{challengeId} AS uuid),
            CAST(#{codeHmac} AS varchar)
        )
    """)
    AccountIdRecoveryResultDTO verifyEmail(
            @Param("challengeId") UUID challengeId,
            @Param("codeHmac") String codeHmac
    );

    @Select("""
        SELECT *
        FROM app_api.complete_identity_account_id_recovery(
            CAST(#{challengeId} AS uuid),
            CAST(#{method} AS varchar),
            CAST(#{providerVerificationId} AS varchar),
            CAST(#{verificationMode} AS varchar),
            CAST(#{verifiedName} AS varchar),
            CAST(#{verifiedBirthDate} AS date),
            CAST(#{verifiedPhone} AS varchar),
            CAST(#{diHmac} AS varchar),
            CAST(#{ciHmac} AS varchar),
            CAST(#{providerVerifiedAt} AS timestamptz)
        )
    """)
    AccountIdRecoveryResultDTO completeIdentity(
            @Param("challengeId") UUID challengeId,
            @Param("method") String method,
            @Param("providerVerificationId") String providerVerificationId,
            @Param("verificationMode") String verificationMode,
            @Param("verifiedName") String verifiedName,
            @Param("verifiedBirthDate") LocalDate verifiedBirthDate,
            @Param("verifiedPhone") String verifiedPhone,
            @Param("diHmac") String diHmac,
            @Param("ciHmac") String ciHmac,
            @Param("providerVerifiedAt") OffsetDateTime providerVerifiedAt
    );

    @Select("SELECT app_api.cleanup_account_id_recovery(#{retentionHours})")
    int cleanup(@Param("retentionHours") int retentionHours);
}
