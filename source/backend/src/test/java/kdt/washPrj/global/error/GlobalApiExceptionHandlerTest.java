package kdt.washPrj.global.error;

import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.constraints.NotBlank;
import kdt.washPrj.auth.AccountSuspendedException;
import kdt.washPrj.auth.AccountWithdrawnException;
import kdt.washPrj.vehicle.DuplicateVehiclePlateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.Set;
import java.util.List;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalApiExceptionHandlerTest {

    private MockMvc mockMvc;
    private GlobalApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalApiExceptionHandler();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void validationErrorsUseCommonResponseFormat() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("요청값을 확인해주세요."))
                .andExpect(jsonPath("$.fieldErrors.name").value("이름은 필수입니다."));
    }

    @Test
    void methodArgumentValidationCanBeHandledDirectly() {
        MethodArgumentNotValidException exception =
                mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError(
                        "request",
                        "name",
                        "이름은 필수입니다."
                )
        ));

        ResponseEntity<ApiErrorResponse> response =
                handler.handleMethodArgumentNotValid(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertEquals(
                "이름은 필수입니다.",
                response.getBody().fieldErrors().get("name")
        );
    }

    @Test
    void responseStatusExceptionsUseCommonResponseFormat() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("대상을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void responseStatusCanBeHandledDirectly() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleResponseStatus(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "대상을 찾을 수 없습니다."
                        )
                );

        assertError(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertEquals(
                "대상을 찾을 수 없습니다.",
                response.getBody().message()
        );
    }

    @Test
    void illegalArgumentsUseCommonResponseFormat() throws Exception {
        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("잘못된 입력입니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void illegalArgumentCanBeHandledDirectly() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleIllegalArgument(
                        new IllegalArgumentException("잘못된 입력입니다.")
                );

        assertError(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertEquals("잘못된 입력입니다.", response.getBody().message());
    }

    @Test
    void bindErrorsUseCommonResponseFormat() {
        BindException exception =
                new BindException(new TestRequest(), "request");
        exception.addError(new FieldError(
                "request",
                "name",
                "이름은 필수입니다."
        ));

        ResponseEntity<ApiErrorResponse> response =
                handler.handleBind(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertEquals(
                "이름은 필수입니다.",
                response.getBody().fieldErrors().get("name")
        );
    }

    @Test
    void constraintViolationsIncludePropertyAndMessage() {
        ConstraintViolation<?> violation =
                mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("plateNumber");
        when(violation.getMessage())
                .thenReturn("차량번호를 확인해주세요.");

        ConstraintViolationException exception =
                new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiErrorResponse> response =
                handler.handleConstraintViolation(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertEquals(
                "차량번호를 확인해주세요.",
                response.getBody().fieldErrors().get("plateNumber")
        );
    }

    @Test
    void methodValidationUsesValidationError() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleMethodValidation(null);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void suspendedAccountUsesLockedStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccountSuspended(
                        new AccountSuspendedException()
                );

        assertError(response, HttpStatus.LOCKED, "ACCOUNT_SUSPENDED");
    }

    @Test
    void withdrawnAccountUsesForbiddenStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccountWithdrawn(
                        new AccountWithdrawnException()
                );

        assertError(response, HttpStatus.FORBIDDEN, "ACCOUNT_WITHDRAWN");
    }

    @Test
    void illegalStateUsesConflictStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleIllegalState(
                        new IllegalStateException("충돌이 발생했습니다.")
                );

        assertError(response, HttpStatus.CONFLICT, "CONFLICT");
        assertEquals("충돌이 발생했습니다.", response.getBody().message());
    }

    @Test
    void duplicateVehiclePlateUsesSpecificConflictCode() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleDuplicateVehiclePlate(
                        new DuplicateVehiclePlateException()
                );

        assertError(
                response,
                HttpStatus.CONFLICT,
                "DUPLICATE_VEHICLE_PLATE"
        );
        assertEquals(
                "이미 등록된 차량 번호입니다.",
                response.getBody().message()
        );
    }

    @Test
    void duplicateCustomerPhoneDoesNotExposeDatabaseConstraint() {
        SQLException sqlException = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"customer_phone_key\"",
                "23505"
        );
        ResponseEntity<ApiErrorResponse> response = handler.handleDataAccess(
                new DataIntegrityViolationException("member update failed", sqlException)
        );

        assertError(response, HttpStatus.CONFLICT, "DUPLICATE_VALUE");
        assertEquals("이미 사용 중인 전화번호입니다.", response.getBody().message());
    }

    @Test
    void malformedRequestUsesBadRequestStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleMalformedRequest(null);

        assertError(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void missingResourceUsesNotFoundStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleNoResource(null);

        assertError(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void unsupportedMethodUsesMethodNotAllowedStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleMethodNotSupported(null);

        assertError(
                response,
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED"
        );
    }

    @Test
    void unsupportedMediaTypeUsesUnsupportedMediaTypeStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleMediaTypeNotSupported(null);

        assertError(
                response,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE"
        );
    }

    @Test
    void unexpectedExceptionUsesInternalServerErrorStatus() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(
                        new Exception("database unavailable")
                );

        assertError(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR"
        );
    }

    private void assertError(
            ResponseEntity<ApiErrorResponse> response,
            HttpStatus status,
            String code
    ) {
        assertEquals(status, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(code, response.getBody().code());
    }

    @RestController
    static class TestController {

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "대상을 찾을 수 없습니다."
            );
        }

        @GetMapping("/test/invalid")
        void invalid() {
            throw new IllegalArgumentException("잘못된 입력입니다.");
        }
    }

    static class TestRequest {
        @NotBlank(message = "이름은 필수입니다.")
        public String name;
    }
}
