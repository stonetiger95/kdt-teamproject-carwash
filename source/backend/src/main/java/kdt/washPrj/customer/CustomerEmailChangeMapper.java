package kdt.washPrj.customer;

import kdt.washPrj.customer.dto.BeginCustomerEmailChangeResultDTO;
import kdt.washPrj.customer.dto.CompleteCustomerEmailChangeResultDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper
public interface CustomerEmailChangeMapper {

    @Select("""
        SELECT *
        FROM app_api.begin_customer_email_change(
            CAST(#{challengeId} AS uuid),
            CAST(#{loginId} AS varchar),
            CAST(#{newEmail} AS varchar),
            CAST(#{otpHmac} AS varchar),
            CAST(#{expiresAt} AS timestamptz),
            #{resendSeconds},
            #{maxAttempts}
        )
    """)
    BeginCustomerEmailChangeResultDTO begin(
            @Param("challengeId") UUID challengeId,
            @Param("loginId") String loginId,
            @Param("newEmail") String newEmail,
            @Param("otpHmac") String otpHmac,
            @Param("expiresAt") OffsetDateTime expiresAt,
            @Param("resendSeconds") int resendSeconds,
            @Param("maxAttempts") int maxAttempts
    );

    @Select("""
        SELECT app_api.mark_customer_email_change_delivery(
            CAST(#{challengeId} AS uuid),
            CAST(#{deliveryStatus} AS varchar)
        )
    """)
    int markDelivery(
            @Param("challengeId") UUID challengeId,
            @Param("deliveryStatus") String deliveryStatus
    );

    @Select("""
        SELECT *
        FROM app_api.complete_customer_email_change(
            CAST(#{challengeId} AS uuid),
            CAST(#{loginId} AS varchar),
            CAST(#{otpHmac} AS varchar)
        )
    """)
    CompleteCustomerEmailChangeResultDTO complete(
            @Param("challengeId") UUID challengeId,
            @Param("loginId") String loginId,
            @Param("otpHmac") String otpHmac
    );
}
