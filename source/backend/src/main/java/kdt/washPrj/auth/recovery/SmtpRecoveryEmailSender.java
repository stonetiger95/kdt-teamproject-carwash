package kdt.washPrj.auth.recovery;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class SmtpRecoveryEmailSender implements RecoveryEmailSender {

    private final PasswordRecoveryProperties properties;
    private final JavaMailSender mailSender;

    public SmtpRecoveryEmailSender(
            PasswordRecoveryProperties properties,
            JavaMailSender mailSender
    ) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    @Override
    public CompletionStage<Void> sendVerificationCode(
            String recipient,
            String code
    ) {
        return send(
                recipient,
                "Straffic 비밀번호 재설정 인증번호",
                "Straffic 비밀번호 재설정 인증번호는 "
                        + code
                        + " 입니다. 인증번호는 "
                        + properties.getChallengeTtlSeconds() / 60
                        + "분 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );
    }

    @Override
    public CompletionStage<Void> sendAccountIdVerificationCode(
            String recipient,
            String code
    ) {
        return send(
                recipient,
                "Straffic 아이디 찾기 인증번호",
                "Straffic 아이디 찾기 인증번호는 "
                        + code
                        + " 입니다. 인증번호는 "
                        + properties.getChallengeTtlSeconds() / 60
                        + "분 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );
    }

    @Override
    public CompletionStage<Void> sendEmailChangeVerificationCode(
            String recipient,
            String code
    ) {
        return send(
                recipient,
                "Straffic 이메일 변경 인증번호",
                "Straffic 이메일 변경 인증번호는 "
                        + code
                        + " 입니다. 인증번호는 "
                        + properties.getChallengeTtlSeconds() / 60
                        + "분 동안 유효합니다. 본인이 요청하지 않았다면 계정 보안을 확인해주세요."
        );
    }

    private CompletionStage<Void> send(
            String recipient,
            String subject,
            String body
    ) {
        properties.requireEmailReady();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getEmailFromAddress());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
