package kdt.washPrj.global.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kdt.washPrj.auth.dto.SignupRequestDTO;
import kdt.washPrj.device.dto.DeviceCreateDTO;
import kdt.washPrj.memberManage.dto.MemberUpdateDTO;
import kdt.washPrj.vehicle.dto.VehicleRequestDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void signupRejectsInvalidMemberFields() {
        SignupRequestDTO request = new SignupRequestDTO();
        request.setLoginId("user_01");
        request.setPassword("123");
        request.setGender("UNKNOWN");
        request.setEmailAddress("invalid-email");

        Set<ConstraintViolation<SignupRequestDTO>> violations =
                validator.validate(request);

        assertViolationFields(
                violations,
                "loginId",
                "password",
                "gender",
                "emailAddress",
                "identityVerificationId",
                "identityProof",
                "identityStateToken"
        );
    }

    @Test
    void signupRequiresGenderIndependentlyOfIdentityProof() {
        SignupRequestDTO request = new SignupRequestDTO();
        request.setLoginId("user01");
        request.setPassword("password");
        request.setEmailAddress("user@example.com");
        request.setIdentityVerificationId(
                "iv0123456789abcdef0123456789abcdef"
        );
        request.setIdentityProof("identity-proof");
        request.setIdentityStateToken("identity-state-token");

        assertViolationFields(
                validator.validate(request),
                "gender"
        );
    }

    @Test
    void vehicleRejectsInvalidPlateAndType() {
        VehicleRequestDTO request = new VehicleRequestDTO();
        request.setPlateNumber("INVALID");
        request.setVehicleType("BUS");

        Set<ConstraintViolation<VehicleRequestDTO>> violations =
                validator.validate(request);

        assertViolationFields(violations, "plateNumber", "vehicleType");
    }

    @Test
    void deviceRejectsInvalidSiteTypeAndStatus() {
        DeviceCreateDTO request = new DeviceCreateDTO();
        request.setWashSiteId(0L);
        request.setDeviceName(" ");
        request.setDeviceType("UNKNOWN");
        request.setStatus("REMOVED");

        Set<ConstraintViolation<DeviceCreateDTO>> violations =
                validator.validate(request);

        assertViolationFields(
                violations,
                "washSiteId",
                "deviceName",
                "deviceType",
                "status"
        );
    }

    @Test
    void memberAdminStatusOnlyUpdateRemainsValid() {
        MemberUpdateDTO request = new MemberUpdateDTO();
        request.setStatus("SUSPENDED");

        assertTrue(validator.validate(request).isEmpty());
    }

    private static void assertViolationFields(
            Set<? extends ConstraintViolation<?>> violations,
            String... fields
    ) {
        for (String field : fields) {
            assertFalse(
                    violations.stream().noneMatch(violation ->
                            field.equals(
                                    violation.getPropertyPath().toString()
                            )
                    ),
                    () -> "Expected violation for field: " + field
            );
        }
    }
}
