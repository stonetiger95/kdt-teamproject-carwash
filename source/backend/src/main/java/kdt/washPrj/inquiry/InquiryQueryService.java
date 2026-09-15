package kdt.washPrj.inquiry;

import kdt.washPrj.inquiry.dto.InquiryAnswerResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryResponseDTO;
import kdt.washPrj.inquiry.dto.InquiryWashSiteDTO;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

@Service
public class InquiryQueryService {

    private final InquiryMapper inquiryMapper;
    private final JsonMapper jsonMapper;

    public InquiryQueryService(
            InquiryMapper inquiryMapper,
            JsonMapper jsonMapper
    ) {
        this.inquiryMapper = inquiryMapper;
        this.jsonMapper = jsonMapper;
    }

    public List<InquiryWashSiteDTO> getUsedWashSites(
            String loginId,
            String viewerRole
    ) {
        return inquiryMapper.selectCustomerUsedWashSites(loginId);
    }

    public List<InquiryResponseDTO> getInquiryList(
            String loginId,
            String viewerRole
    ) {
        return inquiryMapper.selectAuthorizedInquiryList(loginId);
    }

    public InquiryResponseDTO getInquiry(
            Long inquiryId,
            String loginId,
            String viewerRole
    ) {
        InquiryResponseDTO inquiry = inquiryMapper.selectAuthorizedInquiryById(
                loginId,
                inquiryId
        );
        if (inquiry == null) {
            return null;
        }

        inquiry.setAnswers(readAnswers(
                inquiry.getAnswersJson()
        ));
        inquiry.setAnswerCount(inquiry.getAnswers().size());
        return inquiry;
    }

    private List<InquiryAnswerResponseDTO> readAnswers(String answersJson) {
        if (answersJson == null || answersJson.isBlank()) {
            return List.of();
        }

        try {
            return Arrays.asList(jsonMapper.readValue(
                    answersJson,
                    InquiryAnswerResponseDTO[].class
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read inquiry answer data.", exception);
        }
    }
}
