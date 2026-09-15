package kdt.washPrj.auth.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryCleanupService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordRecoveryCleanupService.class);

    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryMapper mapper;
    private final AccountIdRecoveryMapper accountIdRecoveryMapper;

    @Autowired
    public PasswordRecoveryCleanupService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryMapper mapper,
            AccountIdRecoveryMapper accountIdRecoveryMapper
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.accountIdRecoveryMapper = accountIdRecoveryMapper;
    }

    PasswordRecoveryCleanupService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryMapper mapper
    ) {
        this(properties, mapper, null);
    }

    @Scheduled(
            fixedDelayString = "${password-recovery.cleanup-fixed-delay-ms:3600000}",
            initialDelayString = "${password-recovery.cleanup-initial-delay-ms:60000}"
    )
    public void cleanupExpiredRecoveryData() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            int deleted = mapper.cleanup(
                    properties.getCleanupRetentionHours()
            );
            int deletedAccountIdChallenges = accountIdRecoveryMapper == null
                    ? 0
                    : accountIdRecoveryMapper.cleanup(
                            properties.getCleanupRetentionHours()
                    );
            if (deleted > 0) {
                log.info(
                        "Deleted {} expired password recovery challenge(s)",
                        deleted
                );
            }
            if (deletedAccountIdChallenges > 0) {
                log.info(
                        "Deleted {} expired account ID recovery challenge(s)",
                        deletedAccountIdChallenges
                );
            }
        } catch (RuntimeException exception) {
            log.warn("Password recovery cleanup failed");
        }
    }
}
