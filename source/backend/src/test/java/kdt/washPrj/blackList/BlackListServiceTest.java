package kdt.washPrj.blackList;

import kdt.washPrj.blackList.dto.AdminVehicleDetailDTO;
import kdt.washPrj.blackList.dto.VehicleBlackListRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlackListServiceTest {
    private static final String ADMIN_LOGIN_ID = "admin01";

    private BlackListMapper mapper;
    private BlackListService service;

    @BeforeEach
    void setUp() {
        mapper = mock(BlackListMapper.class);
        service = new BlackListService();
        service.mapper = mapper;
        service.jsonMapper = JsonMapper.builder().build();
    }

    @Test
    void getVehicleDetailReadsBundledHistoryJson() {
        AdminVehicleDetailDTO detail = new AdminVehicleDetailDTO();
        detail.setVisitCount(2);
        detail.setEntryExitHistoryJson("[{}]");
        detail.setBlacklistHistoryJson("[{}]");
        when(mapper.getVehicleDetail(ADMIN_LOGIN_ID, "AB123456"))
                .thenReturn(detail);

        AdminVehicleDetailDTO result = service.getVehicleDetail(
                ADMIN_LOGIN_ID,
                " ab-12 3456 "
        );

        assertEquals(1, result.getEntryExitHistory().size());
        assertEquals(1, result.getBlackListHistory().size());
    }

    @Test
    void getVehicleDetailUsesEmptyHistoryWhenJsonIsMissing() {
        AdminVehicleDetailDTO detail = new AdminVehicleDetailDTO();
        detail.setVisitCount(0);
        when(mapper.getVehicleDetail(ADMIN_LOGIN_ID, "AB123456"))
                .thenReturn(detail);

        AdminVehicleDetailDTO result =
                service.getVehicleDetail(ADMIN_LOGIN_ID, "AB123456");

        assertEquals(0, result.getVisitCount());
        assertEquals(0, result.getEntryExitHistory().size());
        assertEquals(0, result.getBlackListHistory().size());
    }

    @Test
    void getVehicleDetailRejectsUnknownVehicle() {
        when(mapper.getVehicleDetail(ADMIN_LOGIN_ID, "AB123456"))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getVehicleDetail(ADMIN_LOGIN_ID, "AB123456")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void registerManualBlackListNormalizesRequest() {
        VehicleBlackListRequestDTO request = request(" repeated violation ");
        when(mapper.addManualBlackList(
                ADMIN_LOGIN_ID,
                "AB123456",
                "repeated violation"
        )).thenReturn(1);

        assertDoesNotThrow(() -> service.addManualBlackList(
                ADMIN_LOGIN_ID,
                " ab-12 3456 ",
                request
        ));

        verify(mapper).addManualBlackList(
                ADMIN_LOGIN_ID,
                "AB123456",
                "repeated violation"
        );
    }

    @Test
    void registerManualBlackListReportsOutdatedDatabasePolicy() {
        VehicleBlackListRequestDTO request = request("reason");
        when(mapper.addManualBlackList(
                ADMIN_LOGIN_ID,
                "AB123456",
                "reason"
        )).thenReturn(-1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.addManualBlackList(
                        ADMIN_LOGIN_ID,
                        "AB123456",
                        request
                )
        );

        assertEquals(
                "블랙리스트 등록 정책이 최신 상태가 아닙니다.",
                exception.getMessage()
        );
    }

    @Test
    void registerManualBlackListRejectsUnknownVehicle() {
        VehicleBlackListRequestDTO request = request("reason");
        when(mapper.addManualBlackList(
                ADMIN_LOGIN_ID,
                "AB123456",
                "reason"
        )).thenReturn(-2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addManualBlackList(
                        ADMIN_LOGIN_ID,
                        "AB123456",
                        request
                )
        );

        assertEquals("차량 조회 이력이 없습니다.", exception.getMessage());
    }

    @Test
    void registerManualBlackListRejectsDuplicateActiveRecord() {
        VehicleBlackListRequestDTO request = request("reason");
        when(mapper.addManualBlackList(
                ADMIN_LOGIN_ID,
                "AB123456",
                "reason"
        )).thenReturn(-3);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.addManualBlackList(
                        ADMIN_LOGIN_ID,
                        "AB123456",
                        request
                )
        );

        assertEquals(
                "이미 수동 블랙리스트에 등록된 차량입니다.",
                exception.getMessage()
        );
    }

    @Test
    void releaseManualBlackListRejectsMissingRecord() {
        when(mapper.releaseManualBlackList(
                ADMIN_LOGIN_ID,
                "AB123456"
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.releaseManualBlackList(
                        ADMIN_LOGIN_ID,
                        "AB123456"
                )
        );
    }

    private VehicleBlackListRequestDTO request(String reason) {
        VehicleBlackListRequestDTO request = new VehicleBlackListRequestDTO();
        request.setReason(reason);
        return request;
    }
}
