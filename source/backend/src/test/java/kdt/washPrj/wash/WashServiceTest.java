package kdt.washPrj.wash;

import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.wash.dto.WashResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WashServiceTest {

    private WashMapper washMapper;
    private WashQueryMapper washQueryMapper;
    private WashService washService;

    @BeforeEach
    void setUp() {
        washMapper = mock(WashMapper.class);
        washQueryMapper = mock(WashQueryMapper.class);
        washService = new WashService(
                washMapper,
                mock(PrepaidService.class),
                new WashQueryService(washQueryMapper)
        );
    }

    @Test
    void nonMemberLookupUsesTrimmedFourDigitPlateSuffix() {
        List<WashResponseDTO> expected = List.of(
                new WashResponseDTO(),
                new WashResponseDTO()
        );

        when(washQueryMapper.selectNonMemberPending("3456"))
                .thenReturn(expected);

        List<WashResponseDTO> result =
                washService.getNonMemberPendingWashHistory(" 3456 ");

        assertSame(expected, result);
        verify(washQueryMapper).selectNonMemberPending("3456");
    }

    @Test
    void nonMemberLookupRejectsFullPlateNumber() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> washService.getNonMemberPendingWashHistory(
                        "127가3456"
                )
        );

        assertEquals(
                "차량번호 뒤 4자리를 입력하세요.",
                exception.getMessage()
        );
        verify(washQueryMapper, never())
                .selectNonMemberPending("127가3456");
    }

    @Test
    void nonMemberLookupRejectsFiveDigitSuffix() {
        assertThrows(
                IllegalArgumentException.class,
                () -> washService.getNonMemberPendingWashHistory(
                        "12345"
                )
        );

        verify(washQueryMapper, never())
                .selectNonMemberPending("12345");
    }

    @Test
    void nonMemberHistoryLookupUsesNormalizedFullPlateNumber() {
        List<WashResponseDTO> expected = List.of(
                new WashResponseDTO(),
                new WashResponseDTO()
        );

        when(washQueryMapper.selectNonMemberHistory("01가7431"))
                .thenReturn(expected);

        List<WashResponseDTO> result =
                washService.getNonMemberWashHistoryList(
                        " 01가-7431 "
                );

        assertSame(expected, result);
        verify(washQueryMapper).selectNonMemberHistory("01가7431");
    }

    @Test
    void nonMemberHistoryLookupRejectsPlateSuffixOnly() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> washService.getNonMemberWashHistoryList(
                        "7431"
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
        assertEquals(
                "올바른 전체 차량번호를 입력하세요.",
                exception.getReason()
        );
        verify(washQueryMapper, never())
                .selectNonMemberHistory("7431");
    }

    @Test
    void memberWashCannotStartBeforePayment() {
        WashResponseDTO washHistory = washHistory(
                "MEMBER",
                "WAITING",
                "ENTERED"
        );
        when(washMapper.selectWashHistoryById(1L))
                .thenReturn(washHistory);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> washService.startWash(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(washMapper, never()).updateWashStart(1L);
    }

    @Test
    void nonMemberWashCanStartBeforeOnsitePayment() {
        WashResponseDTO washHistory = washHistory(
                "NON_MEMBER",
                "WAITING",
                "ENTERED"
        );
        when(washMapper.selectWashHistoryById(1L))
                .thenReturn(washHistory);
        when(washMapper.updateWashStart(1L)).thenReturn(1);

        assertEquals(1, washService.startWash(1L));
        verify(washMapper).updateWashStart(1L);
    }

    @Test
    void washCannotFinishBeforeItStarts() {
        WashResponseDTO washHistory = washHistory(
                "NON_MEMBER",
                "WAITING",
                "ENTERED"
        );
        when(washMapper.selectWashHistoryById(1L))
                .thenReturn(washHistory);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> washService.endWash(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(washMapper, never()).updateWashEnd(1L);
    }

    @Test
    void unpaidWashCannotExit() {
        WashResponseDTO washHistory = washHistory(
                "NON_MEMBER",
                "WAITING",
                "WASH_DONE"
        );
        when(washMapper.selectWashHistoryById(1L))
                .thenReturn(washHistory);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> washService.exitWash(1L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(washMapper, never()).updateExit(1L);
    }

    @Test
    void arbitraryWashStatusIsRejected() {
        WashResponseDTO washHistory = washHistory(
                "NON_MEMBER",
                "WAITING",
                "ENTERED"
        );
        when(washMapper.selectWashHistoryById(1L))
                .thenReturn(washHistory);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> washService.updateWashStatus(1L, "SKIPPED")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(washMapper, never()).updateWashStatus(1L, "SKIPPED");
    }

    private WashResponseDTO washHistory(
            String customerType,
            String paymentStatus,
            String washStatus
    ) {
        WashResponseDTO washHistory = new WashResponseDTO();
        washHistory.setWashHistoryId(1L);
        washHistory.setCustomerType(customerType);
        washHistory.setPaymentStatus(paymentStatus);
        washHistory.setWashStatus(washStatus);
        return washHistory;
    }
}
