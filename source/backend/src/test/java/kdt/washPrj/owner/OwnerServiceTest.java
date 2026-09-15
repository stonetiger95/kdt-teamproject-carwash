package kdt.washPrj.owner;

import kdt.washPrj.owner.dto.ChangePasswordRequestDTO;
import kdt.washPrj.owner.dto.OwnerMyPageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerServiceTest {

    private OwnerMapper mapper;
    private OwnerService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OwnerMapper.class);
        service = new OwnerService(mapper);
    }

    @Test
    void getMyPageReturnsMappedOwner() {
        OwnerMyPageDTO expected = new OwnerMyPageDTO();
        when(mapper.findMyPageByLoginId("owner01")).thenReturn(expected);

        assertSame(expected, service.getMyPage("owner01"));
    }

    @Test
    void updateMyPageReturnsDatabaseResult() {
        OwnerMyPageDTO request = new OwnerMyPageDTO();
        OwnerMyPageDTO updated = new OwnerMyPageDTO();
        when(mapper.updateMyPageAndGet("owner01", request)).thenReturn(updated);

        assertSame(updated, service.updateMyPage("owner01", request));
        verify(mapper).updateMyPageAndGet("owner01", request);
    }

    @Test
    void changePasswordDelegatesCheckedOperation() {
        ChangePasswordRequestDTO request = password("old-password", "new-password");
        when(mapper.changePasswordChecked(
                "owner01",
                "old-password",
                "new-password"
        )).thenReturn(1);

        assertDoesNotThrow(() -> service.changePassword("owner01", request));

        verify(mapper).changePasswordChecked(
                "owner01",
                "old-password",
                "new-password"
        );
    }

    @Test
    void changePasswordRejectsMismatch() {
        ChangePasswordRequestDTO request = password("wrong", "new-password");
        when(mapper.changePasswordChecked("owner01", "wrong", "new-password"))
                .thenReturn(-1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.changePassword("owner01", request)
        );
    }

    private ChangePasswordRequestDTO password(String current, String replacement) {
        ChangePasswordRequestDTO request = new ChangePasswordRequestDTO();
        request.setCurrentPassword(current);
        request.setNewPassword(replacement);
        return request;
    }
}
