package kdt.washPrj.auth.recovery;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpRecoveryEmailSenderTest {

    @Test
    void sendsOtpWithConfiguredFromAddress() {
        PasswordRecoveryProperties properties =
                PasswordRecoveryTestFixtures.properties();
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpRecoveryEmailSender sender = new SmtpRecoveryEmailSender(
                properties,
                mailSender
        );

        sender.sendVerificationCode("recipient@example.com", "123456")
                .toCompletableFuture()
                .join();

        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());

        assertEquals("sender@example.com", message.getValue().getFrom());
        assertArrayEquals(
                new String[]{"recipient@example.com"},
                message.getValue().getTo()
        );
        assertEquals(
                "Straffic 비밀번호 재설정 인증번호",
                message.getValue().getSubject()
        );
        assertTrue(message.getValue().getText().contains("123456"));
    }

    @Test
    void sendsProfileEmailChangeOtpWithDifferentPurpose() {
        PasswordRecoveryProperties properties =
                PasswordRecoveryTestFixtures.properties();
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpRecoveryEmailSender sender = new SmtpRecoveryEmailSender(
                properties,
                mailSender
        );

        sender.sendEmailChangeVerificationCode(
                "new-address@example.com",
                "654321"
        ).toCompletableFuture().join();

        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());

        assertEquals(
                "Straffic 이메일 변경 인증번호",
                message.getValue().getSubject()
        );
        assertTrue(message.getValue().getText().contains("654321"));
    }
}
