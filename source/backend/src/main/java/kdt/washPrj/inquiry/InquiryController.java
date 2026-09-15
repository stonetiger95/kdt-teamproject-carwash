package kdt.washPrj.inquiry;

import jakarta.validation.Valid;
import kdt.washPrj.inquiry.dto.InquiryAnswerDTO;
import kdt.washPrj.inquiry.dto.InquiryRequestDTO;
import kdt.washPrj.inquiry.dto.InquiryRefundRequestDTO;
import kdt.washPrj.inquiry.dto.InquiryResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryUpdateDTO;
import kdt.washPrj.inquiry.dto.InquiryWashSiteDTO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @PostMapping
    public int createInquiry(@Valid @RequestBody InquiryRequestDTO requestDTO) {
        return inquiryService.createInquiry(
                requestDTO,
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

    @GetMapping
    public List<InquiryResponseDTO> getInquiryList() {
        return inquiryService.getInquiryList(
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

    @GetMapping("/used-wash-sites")
    public List<InquiryWashSiteDTO> getUsedWashSites() {
        return inquiryService.getUsedWashSites(
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

    @GetMapping("/{inquiryId}")
    public InquiryResponseDTO getInquiry(@PathVariable Long inquiryId) {
        return inquiryService.getInquiry(
                inquiryId,
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

    @PutMapping("/{inquiryId}")
    public int updateInquiry(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryUpdateDTO requestDTO
    ) {
        return inquiryService.updateInquiry(
                inquiryId,
                requestDTO,
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

    @PatchMapping("/{inquiryId}/answer")
    public int answerInquiry(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerDTO requestDTO
    ) {
        return inquiryService.answerInquiry(
                inquiryId,
                requestDTO,
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

    @PostMapping("/{inquiryId}/refund")
    public int refundInquiry(
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryRefundRequestDTO requestDTO
    ) {
        return inquiryService.refundInquiry(
                inquiryId,
                requestDTO,
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

    @DeleteMapping("/{inquiryId}")
    public int deleteInquiry(@PathVariable Long inquiryId) {
        return inquiryService.deleteInquiry(
                inquiryId,
                AuthRoleContext.getLoginId(),
                AuthRoleContext.getInquiryRole()
        );
    }

}
