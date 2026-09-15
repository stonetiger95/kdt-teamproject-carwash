package kdt.washPrj.auth.recovery;

import java.util.concurrent.CompletionStage;

public interface RecoveryEmailSender {
    CompletionStage<Void> sendVerificationCode(String recipient, String code);

    CompletionStage<Void> sendAccountIdVerificationCode(
            String recipient,
            String code
    );

    CompletionStage<Void> sendEmailChangeVerificationCode(
            String recipient,
            String code
    );
}
