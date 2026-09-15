package kdt.washPrj.auth.recovery;

import kdt.washPrj.auth.recovery.dto.PasswordRecoverySessionResponseDTO;
import kdt.washPrj.auth.recovery.dto.PasswordResetAuthorizationDTO;
import kdt.washPrj.auth.recovery.dto.PasswordResetRequestDTO;
import kdt.washPrj.auth.recovery.dto.PasswordResetResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class PasswordRecoveryService {

    private final PasswordRecoveryProperties properties;
    private final PasswordRecoveryMapper mapper;
    private final PasswordRecoveryCrypto crypto;

    public PasswordRecoveryService(
            PasswordRecoveryProperties properties,
            PasswordRecoveryMapper mapper,
            PasswordRecoveryCrypto crypto
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.crypto = crypto;
    }

    public PasswordRecoverySessionResponseDTO getSession(String rawToken) {
        properties.requireReady();
        if (rawToken == null || rawToken.isBlank()) {
            return PasswordRecoverySessionResponseDTO.unauthorized();
        }

        try {
            PasswordResetAuthorizationDTO authorization =
                    mapper.authorizationStatus(
                            crypto.hmacResetToken(rawToken)
                    );
            if (authorization == null || !authorization.isAuthorized()) {
                return PasswordRecoverySessionResponseDTO.unauthorized();
            }
            return new PasswordRecoverySessionResponseDTO(
                    true,
                    authorization.getExpiresAt(),
                    authorization.getMethod()
            );
        } catch (IllegalArgumentException exception) {
            return PasswordRecoverySessionResponseDTO.unauthorized();
        }
    }

    public PasswordResetResponseDTO reset(
            String rawToken,
            PasswordResetRequestDTO request
    ) {
        properties.requireReady();
        if (rawToken == null || rawToken.isBlank() || request == null) {
            throw unauthorized();
        }

        String newPassword = request.newPassword();
        if (newPassword == null
                || newPassword.length() < 8
                || newPassword.length() > 72
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidPassword();
        }

        String result;
        try {
            result = mapper.consume(
                    crypto.hmacResetToken(rawToken),
                    newPassword
            );
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }

        if ("INVALID_PASSWORD".equals(result)) {
            throw invalidPassword();
        }
        if (!"OK".equals(result)) {
            throw unauthorized();
        }
        return new PasswordResetResponseDTO(
                "비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해주세요."
        );
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "비밀번호 재설정 인증이 유효하지 않거나 만료되었습니다."
        );
    }

    private static IllegalArgumentException invalidPassword() {
        return new IllegalArgumentException(
                "새 비밀번호는 8자 이상이며 UTF-8 기준 72바이트 이하여야 합니다."
        );
    }
}
