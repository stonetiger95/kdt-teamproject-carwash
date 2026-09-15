package kdt.washPrj.memberManage;

import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.memberManage.dto.MemberManageDetailDTO;
import kdt.washPrj.memberManage.dto.MemberManageListDTO;
import kdt.washPrj.memberManage.dto.MemberManageSearchDTO;
import kdt.washPrj.memberManage.dto.MemberUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberManageServiceTest {
    private static final String ADMIN_LOGIN_ID = "admin01";

    private MemberManageMapper mapper;
    private MemberManageService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MemberManageMapper.class);
        service = new MemberManageService();
        service.mapper = mapper;
        service.jsonMapper = JsonMapper.builder().build();
    }

    @Test
    void memberListNormalizesSearchAndBuildsPageFromBundledCount() {
        MemberManageSearchDTO search = new MemberManageSearchDTO();
        MemberManageListDTO member = new MemberManageListDTO();
        member.setTotalElements(11L);
        List<MemberManageListDTO> members = List.of(member);
        when(mapper.getMemberList(ADMIN_LOGIN_ID, search)).thenReturn(members);

        PageResponseDTO<MemberManageListDTO> response =
                service.memberList(ADMIN_LOGIN_ID, search);

        assertEquals("", search.getKeyword());
        assertEquals("", search.getStatus());
        assertEquals("", search.getCustomerType());
        assertSame(members, response.getContent());
        assertEquals(11, response.getTotalElements());
    }

    @Test
    void memberDetailReadsBundledVehicleJson() {
        MemberManageDetailDTO detail = new MemberManageDetailDTO();
        detail.setVehiclesJson("[{}]");
        when(mapper.getMemberDetail(ADMIN_LOGIN_ID, 7)).thenReturn(detail);

        MemberManageDetailDTO actual = service.memberDetail(ADMIN_LOGIN_ID, 7);

        assertSame(detail, actual);
        assertEquals(1, actual.getVehicles().size());
    }

    @Test
    void updateMemberReturnsRefreshedDetail() {
        MemberUpdateDTO request = new MemberUpdateDTO();
        request.setStatus("SUSPENDED");
        MemberManageDetailDTO refreshed = new MemberManageDetailDTO();
        when(mapper.updateMemberDetail(ADMIN_LOGIN_ID, 7, request)).thenReturn(1);
        when(mapper.getMemberDetail(ADMIN_LOGIN_ID, 7)).thenReturn(refreshed);

        MemberManageDetailDTO actual =
                service.updateMember(ADMIN_LOGIN_ID, 7, request);

        assertSame(refreshed, actual);
        verify(mapper).updateMemberDetail(ADMIN_LOGIN_ID, 7, request);
    }

    @Test
    void updateMemberRejectsUnsupportedStatus() {
        MemberUpdateDTO request = new MemberUpdateDTO();
        request.setStatus("WITHDRAWN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateMember(ADMIN_LOGIN_ID, 7, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
