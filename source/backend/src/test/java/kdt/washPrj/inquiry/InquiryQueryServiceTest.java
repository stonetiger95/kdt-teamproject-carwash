package kdt.washPrj.inquiry;

import kdt.washPrj.inquiry.dto.InquiryAnswerResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryWashSiteDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InquiryQueryServiceTest {

    private InquiryMapper inquiryMapper;
    private InquiryQueryService service;

    @BeforeEach
    void setUp() {
        inquiryMapper = mock(InquiryMapper.class);
        service = new InquiryQueryService(
                inquiryMapper,
                JsonMapper.builder().build()
        );
    }

    @Test
    void getUsedWashSitesReturnsCustomerSites() {
        List<InquiryWashSiteDTO> expected =
                List.of(new InquiryWashSiteDTO());
        when(inquiryMapper.selectCustomerUsedWashSites("customer01"))
                .thenReturn(expected);

        List<InquiryWashSiteDTO> actual =
                service.getUsedWashSites("customer01", "CUSTOMER");

        assertSame(expected, actual);
        verify(inquiryMapper).selectCustomerUsedWashSites("customer01");
    }

    @Test
    void getInquiryListReturnsMapperResults() {
        List<InquiryResponseDTO> expected =
                List.of(new InquiryResponseDTO());
        when(inquiryMapper.selectAuthorizedInquiryList("admin01"))
                .thenReturn(expected);

        List<InquiryResponseDTO> actual =
                service.getInquiryList("admin01", "ADMIN");

        assertSame(expected, actual);
        verify(inquiryMapper).selectAuthorizedInquiryList("admin01");
    }

    @Test
    void getInquiryLoadsAnswersForAdmin() {
        InquiryResponseDTO expected = new InquiryResponseDTO();
        expected.setInquiryId(9L);
        expected.setAnswersJson("[{}]");
        when(inquiryMapper.selectAuthorizedInquiryById("admin01", 9L))
                .thenReturn(expected);

        InquiryResponseDTO actual =
                service.getInquiry(9L, "admin01", "ADMIN");

        assertSame(expected, actual);
        assertEquals(1, actual.getAnswerCount());
        verify(inquiryMapper).selectAuthorizedInquiryById("admin01", 9L);
    }
}
