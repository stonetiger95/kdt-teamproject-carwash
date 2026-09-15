package kdt.washPrj.wash;

import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.wash.dto.WashHistorySearchDTO;
import kdt.washPrj.wash.dto.WashResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WashQueryServiceTest {

    private WashQueryMapper mapper;
    private WashQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(WashQueryMapper.class);
        service = new WashQueryService(mapper);
    }

    @Test
    void getWashHistoryUsesAuthorizedDetail() {
        WashResponseDTO expected = new WashResponseDTO();
        when(mapper.selectAuthorizedById("admin01", 1L)).thenReturn(expected);

        assertSame(expected, service.getWashHistory("admin01", 1L));
    }

    @Test
    void getWashHistoryListUsesAuthorizedList() {
        List<WashResponseDTO> expected = List.of(new WashResponseDTO());
        when(mapper.selectAuthorizedList(
                eq("admin01"),
                any(WashHistorySearchDTO.class),
                eq(null),
                eq(0)
        )).thenReturn(expected);

        assertSame(expected, service.getWashHistoryList("admin01"));
    }

    @Test
    void getWashHistoryPageNormalizesSearchAndReadsBundledCount() {
        WashHistorySearchDTO search = searchWithWhitespace();
        WashResponseDTO row = new WashResponseDTO();
        row.setTotalElements(21L);
        List<WashResponseDTO> content = List.of(row);
        when(mapper.selectAuthorizedPage("admin01", search)).thenReturn(content);

        PageResponseDTO<WashResponseDTO> result =
                service.getWashHistoryPage("admin01", search);

        assertSame(content, result.getContent());
        assertEquals(21, result.getTotalElements());
        assertEquals("12가3456", search.getPlateNumber());
        assertEquals("MEMBER", search.getCustomerType());
    }

    @Test
    void customerAndOwnerDetailsUseSameAuthorizedContract() {
        WashResponseDTO customer = new WashResponseDTO();
        WashResponseDTO owner = new WashResponseDTO();
        when(mapper.selectAuthorizedById("customer01", 2L)).thenReturn(customer);
        when(mapper.selectAuthorizedById("owner01", 3L)).thenReturn(owner);

        assertSame(customer, service.getCustomerWashHistory("customer01", 2L));
        assertSame(owner, service.getOwnerWashHistory("owner01", 3L));
    }

    @Test
    void authorizedImageReturnsNotFoundForMissingPath() {
        when(mapper.selectAuthorizedById("customer01", 4L))
                .thenReturn(new WashResponseDTO());
        when(mapper.selectImagePath(4L, "ENTRY")).thenReturn(null);

        ResponseEntity<Resource> result = service.getCustomerWashHistoryImage(
                "customer01",
                4L,
                "entry"
        );

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        verify(mapper).selectImagePath(4L, "ENTRY");
    }

    @Test
    void pendingNonMemberHistoryRequiresFourDigits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getNonMemberPendingWashHistory("123")
        );
    }

    @Test
    void pendingNonMemberHistoryDelegatesNormalizedLastFour() {
        List<WashResponseDTO> expected = List.of(new WashResponseDTO());
        when(mapper.selectNonMemberPending("3456")).thenReturn(expected);

        assertSame(expected, service.getNonMemberPendingWashHistory(" 3456 "));
    }

    @Test
    void nonMemberHistoryNormalizesFullPlate() {
        List<WashResponseDTO> expected = List.of(new WashResponseDTO());
        when(mapper.selectNonMemberHistory("12가3456")).thenReturn(expected);

        assertSame(expected, service.getNonMemberWashHistoryList("12가 3456"));
    }

    private WashHistorySearchDTO searchWithWhitespace() {
        WashHistorySearchDTO search = new WashHistorySearchDTO();
        search.setPlateNumber(" 12가3456 ");
        search.setCustomerType(" MEMBER ");
        search.setPaymentStatus(" SUCCESS ");
        return search;
    }
}
