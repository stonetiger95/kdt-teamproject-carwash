package kdt.washPrj.inquiry;

import kdt.washPrj.inquiry.dto.InquiryAnswerDTO;
import kdt.washPrj.inquiry.dto.InquiryRequestDTO;
import kdt.washPrj.inquiry.dto.InquiryRefundRequestDTO;
import kdt.washPrj.inquiry.dto.InquiryResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryUpdateDTO;
import kdt.washPrj.inquiry.dto.InquiryWashSiteDTO;
import kdt.washPrj.payment.PaymentService;
import kdt.washPrj.payment.dto.PaymentCancelDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class InquiryService {

    private final InquiryMapper inquiryMapper;
    private final PaymentService paymentService;
    private final InquiryQueryService inquiryQueryService;

    public InquiryService(
            InquiryMapper inquiryMapper,
            PaymentService paymentService,
            InquiryQueryService inquiryQueryService
    ) {
        this.inquiryMapper = inquiryMapper;
        this.paymentService = paymentService;
        this.inquiryQueryService = inquiryQueryService;
    }

    @Transactional
    public int createInquiry(InquiryRequestDTO requestDTO, String loginId, String writerRole) {
        if (requestDTO == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Inquiry information is required.");
        }

        InquiryContext context = resolveInquiryContext(
                requestDTO.getInquiryType(),
                requestDTO.getPaymentHistoryId(),
                requestDTO.getWashSiteId(),
                loginId,
                writerRole
        );
        requestDTO.setInquiryType(context.inquiryType());
        requestDTO.setPaymentHistoryId(context.paymentHistoryId());
        requestDTO.setWashSiteId(context.washSiteId());

        return inquiryMapper.insertInquiry(loginId, requestDTO);
    }

    public List<InquiryWashSiteDTO> getUsedWashSites(String loginId, String viewerRole) {
        return inquiryQueryService.getUsedWashSites(loginId, viewerRole);
    }

    public List<InquiryResponseDTO> getInquiryList(String loginId, String viewerRole) {
        return inquiryQueryService.getInquiryList(loginId, viewerRole);
    }

    public InquiryResponseDTO getInquiry(Long inquiryId, String loginId, String viewerRole) {
        return inquiryQueryService.getInquiry(inquiryId, loginId, viewerRole);
    }

    @Transactional
    public int updateInquiry(
            Long inquiryId,
            InquiryUpdateDTO requestDTO,
            String loginId,
            String viewerRole
    ) {
        if (requestDTO == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Inquiry information is required.");
        }

        InquiryContext context = resolveInquiryContext(
                requestDTO.getInquiryType(),
                requestDTO.getPaymentHistoryId(),
                requestDTO.getWashSiteId(),
                loginId,
                viewerRole
        );
        requestDTO.setInquiryType(context.inquiryType());
        requestDTO.setPaymentHistoryId(context.paymentHistoryId());
        requestDTO.setWashSiteId(context.washSiteId());

        int updated = inquiryMapper.updateInquiry(loginId, inquiryId, requestDTO);

        if (updated == -1) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "An answered inquiry cannot be modified."
            );
        }

        return updated;
    }

    @Transactional
    public int answerInquiry(
            Long inquiryId,
            InquiryAnswerDTO requestDTO,
            String answererId,
            String viewerRole
    ) {
        if (requestDTO == null
                || requestDTO.getAnswer() == null
                || requestDTO.getAnswer().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Answer is required.");
        }

        return inquiryMapper.appendInquiryAnswer(
                answererId,
                inquiryId,
                requestDTO.getAnswer().trim()
        );
    }

    public int refundInquiry(
            Long inquiryId,
            InquiryRefundRequestDTO requestDTO,
            String loginId,
            String viewerRole
    ) {
        InquiryResponseDTO inquiry = inquiryMapper.selectAuthorizedInquiryById(
                loginId,
                inquiryId
        );

        if (inquiry == null) {
            throw new ResponseStatusException(NOT_FOUND, "Inquiry was not found.");
        }

        if (!"REFUND".equals(inquiry.getInquiryType())
                || inquiry.getPaymentHistoryId() == null) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "This inquiry is not a refund request."
            );
        }

        if ("CUSTOMER".equals(viewerRole)) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Customers cannot approve refunds."
            );
        }

        PaymentCancelDTO cancelDTO = new PaymentCancelDTO();
        cancelDTO.setRefundAmount(requestDTO.getRefundAmount());
        cancelDTO.setCancelReason(requestDTO.getReason());
        cancelDTO.setRefundReasonCode(requestDTO.getRefundReasonCode());

        String refundAnswer =
                "환불 처리가 완료되었습니다. 환불 금액: "
                        + String.format("%,d원", requestDTO.getRefundAmount());

        int refunded = paymentService.refundPaymentForInquiry(
                inquiry.getPaymentHistoryId(),
                cancelDTO,
                loginId,
                viewerRole,
                inquiryId,
                refundAnswer
        );

        if (refunded == 0) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Refund payment was not found."
            );
        }

        return refunded;
    }

    @Transactional
    public int deleteInquiry(Long inquiryId, String loginId, String viewerRole) {
        int deleted = inquiryMapper.deleteInquiry(loginId, inquiryId);

        if (deleted == -1) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "An answered inquiry cannot be deleted."
            );
        }

        return deleted;
    }

    private InquiryContext resolveInquiryContext(
            String inquiryType,
            Long paymentHistoryId,
            Long washSiteId,
            String loginId,
            String writerRole
    ) {
        if (!"CUSTOMER".equals(writerRole)) {
            return new InquiryContext("ETC", null, null);
        }

        String normalizedType = inquiryType == null || inquiryType.isBlank()
                ? "WASH_SITE"
                : inquiryType.trim().toUpperCase();

        if ("REFUND".equals(normalizedType)) {
            if (paymentHistoryId == null) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "A payment must be selected for a refund inquiry."
                );
            }

            return new InquiryContext(
                    normalizedType,
                    paymentHistoryId,
                    null
            );
        }

        if (!"WASH_SITE".equals(normalizedType)) {
            return new InquiryContext(normalizedType, null, null);
        }

        if (washSiteId == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "A wash site must be selected."
            );
        }

        return new InquiryContext(normalizedType, null, washSiteId);
    }

    private record InquiryContext(
            String inquiryType,
            Long paymentHistoryId,
            Long washSiteId
    ) {
    }

}
