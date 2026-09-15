package kdt.washPrj.wash;

import jakarta.validation.Valid;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.nonMember.NonMemberAccessService;
import kdt.washPrj.nonMember.dto.NonMemberAccessRequestDTO;
import kdt.washPrj.nonMember.dto.NonMemberAccessResponseDTO;
import kdt.washPrj.wash.dto.WashHistorySearchDTO;
import kdt.washPrj.wash.dto.WashPaymentRetryResponseDTO;
import kdt.washPrj.wash.dto.WashResponseDTO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WashController {

    private final WashService washService;
    private final NonMemberAccessService nonMemberAccessService;

    public WashController(
            WashService washService,
            NonMemberAccessService nonMemberAccessService
    ) {
        this.washService = washService;
        this.nonMemberAccessService = nonMemberAccessService;
    }

    @GetMapping("/customer/wash-history")
    public List<WashResponseDTO> getCustomerWashHistoryList(
            Authentication authentication,
            @Valid WashHistorySearchDTO search
    ) {
        return washService.getCustomerWashHistoryList(
                authentication.getName(),
                search
        );
    }

    @GetMapping("/customer/wash-history/{washHistoryId}")
    public WashResponseDTO getCustomerWashHistory(
            Authentication authentication,
            @PathVariable Long washHistoryId
    ) {
        return washService.getCustomerWashHistory(authentication.getName(), washHistoryId);
    }

    @GetMapping("/customer/wash-history/{washHistoryId}/images/{imageType}")
    public ResponseEntity<Resource> getCustomerWashHistoryImage(
            Authentication authentication,
            @PathVariable Long washHistoryId,
            @PathVariable String imageType
    ) {
        return washService.getCustomerWashHistoryImage(
                authentication.getName(),
                washHistoryId,
                imageType
        );
    }

    @PostMapping("/customer/wash-history/{washHistoryId}/retry-payment")
    public ResponseEntity<WashPaymentRetryResponseDTO> retryCustomerWashPayment(
            Authentication authentication,
            @PathVariable Long washHistoryId
    ) {
        WashPaymentRetryResponseDTO response = washService.retryCustomerWashPayment(
                authentication.getName(),
                washHistoryId
        );
        HttpStatus status = "PAYMENT_REQUIRED".equals(response.result())
                ? HttpStatus.CONFLICT
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/owner/wash-history")
    public List<WashResponseDTO> getOwnerWashHistoryList(
            Authentication authentication
    ) {
        return washService.getOwnerWashHistoryList(authentication.getName());
    }

    @GetMapping("/owner/wash-history/page")
    public PageResponseDTO<WashResponseDTO> getOwnerWashHistoryPage(
            Authentication authentication,
            @Valid WashHistorySearchDTO search
    ) {
        return washService.getOwnerWashHistoryPage(
                authentication.getName(),
                search
        );
    }

    @GetMapping("/owner/wash-history/{washHistoryId}")
    public WashResponseDTO getOwnerWashHistory(
            Authentication authentication,
            @PathVariable Long washHistoryId
    ) {
        return washService.getOwnerWashHistory(authentication.getName(), washHistoryId);
    }

    @GetMapping("/owner/wash-history/{washHistoryId}/images/{imageType}")
    public ResponseEntity<Resource> getOwnerWashHistoryImage(
            Authentication authentication,
            @PathVariable Long washHistoryId,
            @PathVariable String imageType
    ) {
        return washService.getOwnerWashHistoryImage(
                authentication.getName(),
                washHistoryId,
                imageType
        );
    }

    @GetMapping("/admin/wash-history")
    public List<WashResponseDTO> getAdminWashHistoryList(
            Authentication authentication
    ) {
        return washService.getWashHistoryList(authentication.getName());
    }

    @GetMapping("/admin/wash-history/page")
    public PageResponseDTO<WashResponseDTO> getAdminWashHistoryPage(
            Authentication authentication,
            @Valid WashHistorySearchDTO search
    ) {
        return washService.getWashHistoryPage(authentication.getName(), search);
    }

    @GetMapping("/admin/wash-history/{washHistoryId}")
    public WashResponseDTO getAdminWashHistory(
            Authentication authentication,
            @PathVariable Long washHistoryId
    ) {
        return washService.getWashHistory(authentication.getName(), washHistoryId);
    }

    @GetMapping("/admin/wash-history/{washHistoryId}/images/{imageType}")
    public ResponseEntity<Resource> getAdminWashHistoryImage(
            Authentication authentication,
            @PathVariable Long washHistoryId,
            @PathVariable String imageType
    ) {
        return washService.getAdminWashHistoryImage(
                authentication.getName(),
                washHistoryId,
                imageType
        );
    }

    @GetMapping("/public/wash-history/pending")
    public List<WashResponseDTO>
    getNonMemberPendingWashHistory(
            @RequestParam String plateLast4
    ) {
        return washService
                .getNonMemberPendingWashHistory(
                        plateLast4
                );
    }

    @GetMapping("/public/wash-history")
    public List<WashResponseDTO>
    getNonMemberWashHistoryList(
            @RequestParam String plateNumber,
            @RequestHeader(
                    value = NonMemberAccessService.ACCESS_HEADER,
                    required = false
            ) String accessToken
    ) {
        nonMemberAccessService.requirePlateAccess(
                accessToken,
                plateNumber
        );

        return washService
                .getNonMemberWashHistoryList(
                        plateNumber
                );
    }

    @PostMapping("/public/wash-history/access")
    public NonMemberAccessResponseDTO issueNonMemberAccessToken(
            @Valid @RequestBody NonMemberAccessRequestDTO requestDTO
    ) {
        return nonMemberAccessService.issueAccessToken(
                requestDTO == null
                        ? null
                        : requestDTO.getPlateNumber()
        );
    }
}
