package kdt.washPrj.nonMember;

import kdt.washPrj.nonMember.dto.NonMemberAccessResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonMemberAccessServiceTest {

    private static final String SECRET =
            "test-secret-key-that-is-at-least-thirty-two-bytes-long";

    private NonMemberAccessMapper mapper;
    private NonMemberAccessService service;

    @BeforeEach
    void setUp() {
        mapper = mock(NonMemberAccessMapper.class);
        service = new NonMemberAccessService(
                mapper,
                SECRET,
                900
        );
    }

    @Test
    void issuedTokenAllowsMatchingWashHistory() {
        when(mapper.existsNonMemberPlateNumber("01가7431"))
                .thenReturn(true);
        when(mapper.selectNonMemberPlateNumber(205L))
                .thenReturn("01가7431");

        NonMemberAccessResponseDTO response =
                service.issueAccessToken("01가7431");

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getExpiresAt());
        assertDoesNotThrow(() ->
                service.requireWashHistoryAccess(
                        response.getAccessToken(),
                        205L
                )
        );
    }

    @Test
    void issuedTokenAllowsEquivalentFormattedPlate() {
        when(mapper.existsNonMemberPlateNumber("01가7431"))
                .thenReturn(true);

        String accessToken = service
                .issueAccessToken("01가7431")
                .getAccessToken();

        assertDoesNotThrow(() ->
                service.requirePlateAccess(
                        accessToken,
                        "01가 7431"
                )
        );
    }

    @Test
    void tokenCannotAccessAnotherPlate() {
        when(mapper.existsNonMemberPlateNumber("01가7431"))
                .thenReturn(true);
        when(mapper.selectNonMemberPlateNumber(206L))
                .thenReturn("11저8819");

        String accessToken = service
                .issueAccessToken("01가7431")
                .getAccessToken();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requireWashHistoryAccess(
                        accessToken,
                        206L
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void missingTokenIsRejected() {
        when(mapper.selectNonMemberPlateNumber(205L))
                .thenReturn("01가7431");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requireWashHistoryAccess(null, 205L)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }
}
