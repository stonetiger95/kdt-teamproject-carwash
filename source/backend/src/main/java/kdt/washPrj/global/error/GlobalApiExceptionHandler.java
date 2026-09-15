package kdt.washPrj.global.error;

import jakarta.validation.ConstraintViolationException;
import kdt.washPrj.auth.AccountSuspendedException;
import kdt.washPrj.auth.AccountWithdrawnException;
import kdt.washPrj.auth.NaverOAuthException;
import kdt.washPrj.auth.social.SocialOAuthException;
import kdt.washPrj.vehicle.DuplicateVehiclePlateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        return validationError(exception.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBind(BindException exception) {
        return validationError(exception.getBindingResult());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                )
        );
        return badRequest("VALIDATION_ERROR", "요청값을 확인해주세요.", fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception
    ) {
        return badRequest("VALIDATION_ERROR", "요청값을 확인해주세요.", Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception
    ) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = status.getReasonPhrase();
        }

        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(status.name(), message));
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountSuspended(
            AccountSuspendedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(ApiErrorResponse.of("ACCOUNT_SUSPENDED", exception.getMessage()));
    }

    @ExceptionHandler(AccountWithdrawnException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountWithdrawn(
            AccountWithdrawnException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("ACCOUNT_WITHDRAWN", exception.getMessage()));
    }

    @ExceptionHandler(NaverOAuthException.class)
    public ResponseEntity<ApiErrorResponse> handleNaverOAuth(
            NaverOAuthException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(SocialOAuthException.class)
    public ResponseEntity<ApiErrorResponse> handleSocialOAuth(
            SocialOAuthException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return badRequest("INVALID_REQUEST", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateVehiclePlateException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateVehiclePlate(
            DuplicateVehiclePlateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        "DUPLICATE_VEHICLE_PLATE",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(
            DataAccessException exception
    ) {
        SQLException postgresException = findSqlException(exception);
        if (postgresException == null) {
            log.error("Unhandled database exception", exception);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.of(
                            "DATABASE_ERROR",
                            "데이터 처리 중 오류가 발생했습니다."
                    ));
        }

        String sqlState = postgresException.getSQLState();
        String databaseMessage = postgresException.getMessage();
        if (databaseMessage != null) {
            databaseMessage = databaseMessage.lines().findFirst().orElse(databaseMessage);
            int separator = databaseMessage.indexOf(": ");
            if (separator >= 0 && separator + 2 < databaseMessage.length()) {
                databaseMessage = databaseMessage.substring(separator + 2);
            }
        }
        String message = databaseMessage == null || databaseMessage.isBlank()
                ? "데이터 요청을 처리할 수 없습니다."
                : databaseMessage;

        return switch (sqlState == null ? "" : sqlState) {
            case "42501" -> ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of("FORBIDDEN", message));
            case "P0002" -> ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of("NOT_FOUND", message));
            case "23505" -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of(
                            "DUPLICATE_VALUE",
                            databaseMessage != null && databaseMessage.contains("customer_phone_key")
                                    ? "이미 사용 중인 전화번호입니다."
                                    : "이미 등록된 값입니다. 입력값을 확인해 주세요."
                    ));
            case "23503" -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of(
                            "DATA_IN_USE",
                            "연결된 데이터가 있어 요청을 처리할 수 없습니다."
                    ));
            case "P0001" -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("CONFLICT", message));
            case "40P01", "40001", "55P03" -> ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "1")
                    .body(ApiErrorResponse.of(
                            "DATABASE_BUSY",
                            "다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
                    ));
            case "22023", "23502", "23514" -> badRequest(
                    "INVALID_REQUEST",
                    message,
                    Map.of()
            );
            default -> {
                log.error("Unhandled PostgreSQL exception. sqlState={}", sqlState, exception);
                yield ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiErrorResponse.of(
                                "DATABASE_ERROR",
                                "데이터 처리 중 오류가 발생했습니다."
                        ));
            }
        };
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception) {
        return badRequest(
                "INVALID_REQUEST",
                "요청 형식 또는 필수값을 확인해주세요.",
                Map.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            NoResourceFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiErrorResponse.of("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        "INTERNAL_SERVER_ERROR",
                        "요청 처리 중 오류가 발생했습니다."
                ));
    }

    private ResponseEntity<ApiErrorResponse> validationError(BindingResult bindingResult) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return badRequest("VALIDATION_ERROR", "요청값을 확인해주세요.", fieldErrors);
    }

    private SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private ResponseEntity<ApiErrorResponse> badRequest(
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
        String resolvedMessage = message == null || message.isBlank()
                ? "잘못된 요청입니다."
                : message;
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(code, resolvedMessage, fieldErrors));
    }
}
