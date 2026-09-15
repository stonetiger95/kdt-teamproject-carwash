package kdt.washPrj.inquiry;

import kdt.washPrj.inquiry.dto.InquiryAnswerDTO;
import kdt.washPrj.inquiry.dto.InquiryRefundRequestDTO;
import kdt.washPrj.inquiry.dto.InquiryRequestDTO;
import kdt.washPrj.inquiry.dto.InquiryResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryUpdateDTO;
import kdt.washPrj.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InquiryServiceTest {

    private InquiryMapper inquiryMapper;
    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryMapper = mock(InquiryMapper.class);
        inquiryService = new InquiryService(
                inquiryMapper,
                mock(PaymentService.class),
                mock(InquiryQueryService.class)
        );
    }

    @Test
    void createInquiryRejectsNullRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inquiryService.createInquiry(null, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void ownerInquiryIsNormalizedToEtcWithoutLinkedData() {
        InquiryRequestDTO request = request("REFUND");
        request.setPaymentHistoryId(31L);
        request.setWashSiteId(7L);
        when(inquiryMapper.insertInquiry("owner01", request)).thenReturn(1);

        int result = inquiryService.createInquiry(request, "owner01", "OWNER");

        assertEquals(1, result);
        assertEquals("ETC", request.getInquiryType());
        assertNull(request.getPaymentHistoryId());
        assertNull(request.getWashSiteId());
        verify(inquiryMapper).insertInquiry("owner01", request);
    }

    @Test
    void customerRefundInquiryKeepsPaymentAndClearsWashSite() {
        InquiryRequestDTO request = request("refund");
        request.setPaymentHistoryId(31L);
        request.setWashSiteId(7L);
        when(inquiryMapper.insertInquiry("customer01", request)).thenReturn(1);

        int result = inquiryService.createInquiry(request, "customer01", "CUSTOMER");

        assertEquals(1, result);
        assertEquals("REFUND", request.getInquiryType());
        assertEquals(31L, request.getPaymentHistoryId());
        assertNull(request.getWashSiteId());
    }

    @Test
    void refundInquiryRequiresPaymentHistory() {
        InquiryRequestDTO request = request("REFUND");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inquiryService.createInquiry(request, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void washSiteInquiryRequiresWashSite() {
        InquiryRequestDTO request = request("WASH_SITE");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inquiryService.createInquiry(request, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void answeredInquiryCannotBeUpdated() {
        InquiryUpdateDTO request = new InquiryUpdateDTO();
        request.setInquiryType("ETC");
        when(inquiryMapper.updateInquiry("customer01", 10L, request)).thenReturn(-1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inquiryService.updateInquiry(10L, request, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void blankAnswerIsRejected() {
        InquiryAnswerDTO request = answer("   ");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inquiryService.answerInquiry(10L, request, "admin01", "ADMIN")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void answerIsTrimmedAndAppended() {
        InquiryAnswerDTO request = answer("  처리했습니다.  ");
        when(inquiryMapper.appendInquiryAnswer("admin01", 10L, "처리했습니다."))
                .thenReturn(1);

        int result = inquiryService.answerInquiry(10L, request, "admin01", "ADMIN");

        assertEquals(1, result);
        verify(inquiryMapper).appendInquiryAnswer("admin01", 10L, "처리했습니다.");
    }

    @Test
    void customerCannotApproveRefund() {
        InquiryResponseDTO inquiry = new InquiryResponseDTO();
        inquiry.setInquiryType("REFUND");
        inquiry.setPaymentHistoryId(31L);
        when(inquiryMapper.selectAuthorizedInquiryById("customer01", 10L))
                .thenReturn(inquiry);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inquiryService.refundInquiry(
                        10L,
                        new InquiryRefundRequestDTO(),
                        "customer01",
                        "CUSTOMER"
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void answeredInquiryCannotBeDeleted() {
        when(inquiryMapper.deleteInquiry("customer01", 10L)).thenReturn(-1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inquiryService.deleteInquiry(10L, "customer01", "CUSTOMER")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private InquiryRequestDTO request(String type) {
        InquiryRequestDTO request = new InquiryRequestDTO();
        request.setTitle("문의");
        request.setContent("문의 내용");
        request.setInquiryType(type);
        return request;
    }

    private InquiryAnswerDTO answer(String value) {
        InquiryAnswerDTO answer = new InquiryAnswerDTO();
        answer.setAnswer(value);
        return answer;
    }
}
