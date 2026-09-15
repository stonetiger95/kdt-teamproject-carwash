package kdt.washPrj.customer;

import kdt.washPrj.customer.dto.ChangePasswordRequestDTO;
import kdt.washPrj.customer.dto.CustomerMyPageDTO;
import kdt.washPrj.customer.dto.WithdrawRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    private CustomerMapper mapper;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CustomerMapper.class);
        service = new CustomerService(mapper);
    }

    @Test
    void getMyPageReturnsMappedCustomer() {
        CustomerMyPageDTO expected = new CustomerMyPageDTO();
        when(mapper.findMyPageByLoginId("customer01")).thenReturn(expected);

        assertSame(expected, service.getMyPage("customer01"));
    }

    @Test
    void getMyPageRejectsMissingCustomer() {
        when(mapper.findMyPageByLoginId("missing")).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getMyPage("missing")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateMyPageReturnsDatabaseResult() {
        CustomerMyPageDTO request = new CustomerMyPageDTO();
        CustomerMyPageDTO updated = new CustomerMyPageDTO();
        when(mapper.updateMyPageAndGet("customer01", request)).thenReturn(updated);

        assertSame(updated, service.updateMyPage("customer01", request));
        verify(mapper).updateMyPageAndGet("customer01", request);
    }

    @Test
    void changePasswordDelegatesCheckedOperation() {
        ChangePasswordRequestDTO request = password("old-password", "new-password");
        when(mapper.changePasswordChecked(
                "customer01",
                "old-password",
                "new-password"
        )).thenReturn(1);

        assertDoesNotThrow(() -> service.changePassword("customer01", request));

        verify(mapper).changePasswordChecked(
                "customer01",
                "old-password",
                "new-password"
        );
    }

    @Test
    void changePasswordRejectsCurrentPasswordMismatch() {
        ChangePasswordRequestDTO request = password("wrong", "new-password");
        when(mapper.changePasswordChecked("customer01", "wrong", "new-password"))
                .thenReturn(-1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.changePassword("customer01", request)
        );
    }

    @Test
    void withdrawDelegatesCheckedOperation() {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setCurrentPassword("password");
        when(mapper.withdrawChecked("customer01", "password")).thenReturn(1);

        assertDoesNotThrow(() -> service.withdraw("customer01", request));

        verify(mapper).withdrawChecked("customer01", "password");
    }

    @Test
    void withdrawRejectsRemainingBalance() {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setCurrentPassword("password");
        when(mapper.withdrawChecked("customer01", "password")).thenReturn(-2);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.withdraw("customer01", request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private ChangePasswordRequestDTO password(String current, String replacement) {
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setCurrentPassword(current);
        request.setNewPassword(replacement);
        return request;
    }
}
