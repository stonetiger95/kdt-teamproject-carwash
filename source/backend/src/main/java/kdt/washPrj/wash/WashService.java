package kdt.washPrj.wash;

import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import kdt.washPrj.wash.dto.WashHistorySearchDTO;
import kdt.washPrj.wash.dto.WashPaymentRetryResponseDTO;
import kdt.washPrj.wash.dto.WashResponseDTO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WashService {

    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_WAITING = "WAITING";
    private static final String PAYMENT_FAILED = "FAILED";
    private static final String PAYMENT_CANCELED = "CANCELED";
    private static final String CUSTOMER_MEMBER = "MEMBER";
    private static final String CUSTOMER_NON_MEMBER = "NON_MEMBER";
    private static final String WASH_ENTERED = "ENTERED";
    private static final String WASHING = "WASHING";
    private static final String WASH_DONE = "WASH_DONE";
    private static final String WASH_EXITED = "EXITED";
    private static final Set<String> PAYMENT_STATUSES = Set.of(
            PAYMENT_WAITING,
            PAYMENT_SUCCESS,
            PAYMENT_FAILED,
            PAYMENT_CANCELED
    );
    private final WashMapper washMapper;
    private final PrepaidService prepaidService;
    private final WashQueryService washQueryService;

    public WashService(
            WashMapper washMapper,
            PrepaidService prepaidService,
            WashQueryService washQueryService
    ) {
        this.washMapper = washMapper;
        this.prepaidService = prepaidService;
        this.washQueryService = washQueryService;
    }

    public WashResponseDTO getWashHistory(String loginId, Long washHistoryId) {
        return washQueryService.getWashHistory(loginId, washHistoryId);
    }

    public List<WashResponseDTO> getWashHistoryList(String loginId) {
        return washQueryService.getWashHistoryList(loginId);
    }

    public PageResponseDTO<WashResponseDTO> getWashHistoryPage(
            String loginId,
            WashHistorySearchDTO search
    ) {
        return washQueryService.getWashHistoryPage(loginId, search);
    }

    public List<WashResponseDTO> getCustomerWashHistoryList(
            String loginId,
            WashHistorySearchDTO search
    ) {
        return washQueryService.getCustomerWashHistoryList(loginId, search);
    }

    public WashResponseDTO getCustomerWashHistory(String loginId, Long washHistoryId) {
        return washQueryService.getCustomerWashHistory(loginId, washHistoryId);
    }

    public List<WashResponseDTO> getOwnerWashHistoryList(String loginId) {
        return washQueryService.getOwnerWashHistoryList(loginId);
    }

    public PageResponseDTO<WashResponseDTO> getOwnerWashHistoryPage(
            String loginId,
            WashHistorySearchDTO search
    ) {
        return washQueryService.getOwnerWashHistoryPage(loginId, search);
    }

    public WashResponseDTO getOwnerWashHistory(String loginId, Long washHistoryId) {
        return washQueryService.getOwnerWashHistory(loginId, washHistoryId);
    }

    public ResponseEntity<Resource> getCustomerWashHistoryImage(
            String loginId,
            Long washHistoryId,
            String imageType
    ) {
        return washQueryService.getCustomerWashHistoryImage(
                loginId,
                washHistoryId,
                imageType
        );
    }

    public ResponseEntity<Resource> getOwnerWashHistoryImage(
            String loginId,
            Long washHistoryId,
            String imageType
    ) {
        return washQueryService.getOwnerWashHistoryImage(
                loginId,
                washHistoryId,
                imageType
        );
    }

    public ResponseEntity<Resource> getAdminWashHistoryImage(
            String loginId,
            Long washHistoryId,
            String imageType
    ) {
        return washQueryService.getAdminWashHistoryImage(
                loginId,
                washHistoryId,
                imageType
        );
    }

    @Transactional
    public WashPaymentRetryResponseDTO retryCustomerWashPayment(
            String loginId,
            Long washHistoryId
    ) {
        WashResponseDTO washHistory = requireWashHistory(
                washQueryService.getCustomerWashHistory(loginId, washHistoryId)
        );

        if (isPaid(washHistory.getPaymentStatus())) {
            return retryResponse(
                    washHistory,
                    "ALREADY_PAID",
                    null,
                    "이미 결제가 완료된 세차 내역입니다."
            );
        }

        PrepaidUseResponseDTO payment = prepaidService.autoUseWithBillingTopUp(
                washHistory.getCustomerId(),
                washHistoryId
        );

        if (payment != null) {
            return new WashPaymentRetryResponseDTO(
                    washHistoryId,
                    "SUCCESS",
                    PAYMENT_SUCCESS,
                    payment.getAmount() == null ? washHistory.getAmount() : payment.getAmount().longValue(),
                    payment.getBalance(),
                    payment.getPaymentHistoryId(),
                    "결제가 완료되었습니다."
            );
        }

        WashResponseDTO refreshed = requireWashHistory(
                washQueryService.getCustomerWashHistory(loginId, washHistoryId)
        );

        if (isPaid(refreshed.getPaymentStatus())) {
            return retryResponse(
                    refreshed,
                    "ALREADY_PAID",
                    null,
                    "이미 결제가 완료된 세차 내역입니다."
            );
        }

        return retryResponse(
                refreshed,
                "PAYMENT_REQUIRED",
                null,
                "잔액이 부족하거나 사용할 수 있는 자동결제 카드가 없습니다."
        );
    }

    @Transactional
    public int startOwnerWash(String loginId, Long washHistoryId) {
        WashResponseDTO washHistory = requireWashHistory(
                washQueryService.getOwnerWashHistory(loginId, washHistoryId)
        );
        validateCanStart(washHistory);
        return requireTransitionUpdated(
                washMapper.updateOwnerWashStart(loginId, washHistoryId)
        );
    }

    @Transactional
    public int endOwnerWash(String loginId, Long washHistoryId) {
        WashResponseDTO washHistory = requireWashHistory(
                washQueryService.getOwnerWashHistory(loginId, washHistoryId)
        );
        validateCanEnd(washHistory);
        return requireTransitionUpdated(
                washMapper.updateOwnerWashEnd(loginId, washHistoryId)
        );
    }

    @Transactional
    public int exitOwnerWash(String loginId, Long washHistoryId) {
        WashResponseDTO washHistory = requireWashHistory(
                washQueryService.getOwnerWashHistory(loginId, washHistoryId)
        );
        validateCanExit(washHistory);
        return requireTransitionUpdated(
                washMapper.updateOwnerExit(loginId, washHistoryId)
        );
    }

    @Transactional
    public int updateOwnerPaymentStatus(String loginId, Long washHistoryId, String paymentStatus) {
        WashResponseDTO washHistory = requireWashHistory(
                washQueryService.getOwnerWashHistory(loginId, washHistoryId)
        );
        String normalizedStatus = normalizePaymentStatus(paymentStatus);

        if (samePaymentStatus(washHistory.getPaymentStatus(), normalizedStatus)) {
            return 1;
        }

        validatePaymentStatusChange(washHistory, normalizedStatus);
        return requireTransitionUpdated(
                washMapper.updateOwnerPaymentStatus(
                        loginId,
                        washHistoryId,
                        normalizedStatus
                )
        );
    }

    @Transactional
    public int updateOwnerWashStatus(String loginId, Long washHistoryId, String washStatus) {
        String normalizedStatus = normalizeWashStatus(washStatus);
        WashResponseDTO washHistory = requireWashHistory(
                washQueryService.getOwnerWashHistory(loginId, washHistoryId)
        );

        if (normalizedStatus.equals(normalizeWashStatus(washHistory.getWashStatus()))) {
            return 1;
        }

        return switch (normalizedStatus) {
            case WASHING -> startOwnerWash(loginId, washHistoryId);
            case WASH_DONE -> endOwnerWash(loginId, washHistoryId);
            case WASH_EXITED -> exitOwnerWash(loginId, washHistoryId);
            default -> throw invalidWashStatusTransition();
        };
    }

    @Transactional
    public int startWash(Long washHistoryId) {
        WashResponseDTO washHistory = requireWashHistory(
                washMapper.selectWashHistoryById(washHistoryId)
        );
        validateCanStart(washHistory);
        return requireTransitionUpdated(
                washMapper.updateWashStart(washHistoryId)
        );
    }

    @Transactional
    public int endWash(Long washHistoryId) {
        WashResponseDTO washHistory = requireWashHistory(
                washMapper.selectWashHistoryById(washHistoryId)
        );
        validateCanEnd(washHistory);
        return requireTransitionUpdated(
                washMapper.updateWashEnd(washHistoryId)
        );
    }

    @Transactional
    public int exitWash(Long washHistoryId) {
        WashResponseDTO washHistory = requireWashHistory(
                washMapper.selectWashHistoryById(washHistoryId)
        );
        validateCanExit(washHistory);
        return requireTransitionUpdated(
                washMapper.updateExit(washHistoryId)
        );
    }

    @Transactional
    public int updatePaymentStatus(Long washHistoryId, String paymentStatus) {
        WashResponseDTO washHistory = requireWashHistory(
                washMapper.selectWashHistoryById(washHistoryId)
        );
        String normalizedStatus = normalizePaymentStatus(paymentStatus);

        if (samePaymentStatus(washHistory.getPaymentStatus(), normalizedStatus)) {
            return 1;
        }

        validatePaymentStatusChange(washHistory, normalizedStatus);
        return requireTransitionUpdated(
                washMapper.updatePaymentStatus(
                        washHistoryId,
                        normalizedStatus
                )
        );
    }

    @Transactional
    public int updateWashStatus(Long washHistoryId, String washStatus) {
        String normalizedStatus = normalizeWashStatus(washStatus);
        WashResponseDTO washHistory = requireWashHistory(
                washMapper.selectWashHistoryById(washHistoryId)
        );

        if (normalizedStatus.equals(normalizeWashStatus(washHistory.getWashStatus()))) {
            return 1;
        }

        return switch (normalizedStatus) {
            case WASHING -> startWash(washHistoryId);
            case WASH_DONE -> endWash(washHistoryId);
            case WASH_EXITED -> exitWash(washHistoryId);
            default -> throw invalidWashStatusTransition();
        };
    }

    private void validateCanStart(WashResponseDTO washHistory) {
        String washStatus = normalizeWashStatus(washHistory.getWashStatus());

        if (!WASH_ENTERED.equals(washStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "입차 상태의 세차 내역만 세차를 시작할 수 있습니다."
            );
        }

        if (CUSTOMER_MEMBER.equals(washHistory.getCustomerType())
                && !isPaid(washHistory.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "회원 차량은 결제 완료 후 세차를 시작할 수 있습니다."
            );
        }

        if (!CUSTOMER_MEMBER.equals(washHistory.getCustomerType())
                && !CUSTOMER_NON_MEMBER.equals(washHistory.getCustomerType())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "세차 내역의 고객 구분을 확인해주세요."
            );
        }
    }

    private void validateCanEnd(WashResponseDTO washHistory) {
        if (!WASHING.equals(normalizeWashStatus(washHistory.getWashStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "세차 중인 내역만 완료할 수 있습니다."
            );
        }
    }

    private void validateCanExit(WashResponseDTO washHistory) {
        if (!WASH_DONE.equals(normalizeWashStatus(washHistory.getWashStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "세차 완료 상태에서만 출차할 수 있습니다."
            );
        }

        if (!isPaid(washHistory.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결제 완료 후 출차할 수 있습니다."
            );
        }
    }

    private void validatePaymentStatusChange(
            WashResponseDTO washHistory,
            String targetStatus
    ) {
        if (isPaid(washHistory.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "완료된 결제 상태는 변경할 수 없습니다."
            );
        }

        if (!PAYMENT_STATUSES.contains(targetStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "허용되지 않는 결제 상태입니다."
            );
        }
    }

    private int requireTransitionUpdated(int updated) {
        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "세차 상태가 이미 변경되었거나 처리 조건을 충족하지 않습니다."
            );
        }
        return updated;
    }

    private String normalizePaymentStatus(String paymentStatus) {
        String normalized = paymentStatus == null
                ? ""
                : paymentStatus.trim().toUpperCase(Locale.ROOT);

        if ("DONE".equals(normalized)) {
            return PAYMENT_SUCCESS;
        }
        if ("READY".equals(normalized)) {
            return PAYMENT_WAITING;
        }
        if (!PAYMENT_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "허용되지 않는 결제 상태입니다."
            );
        }
        return normalized;
    }

    private boolean samePaymentStatus(String current, String target) {
        return normalizePaymentStatus(current).equals(target);
    }

    private String normalizeWashStatus(String washStatus) {
        String normalized = washStatus == null
                ? ""
                : washStatus.trim().toUpperCase(Locale.ROOT);

        return "WAITING".equals(normalized)
                ? WASH_ENTERED
                : normalized;
    }

    private ResponseStatusException invalidWashStatusTransition() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "세차 상태는 입차, 세차 중, 세차 완료, 출차 순서로만 변경할 수 있습니다."
        );
    }

    private WashResponseDTO requireWashHistory(WashResponseDTO washHistory) {
        if (washHistory == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "세차 내역을 찾을 수 없습니다.");
        }

        return washHistory;
    }

    private boolean isPaid(String paymentStatus) {
        return PAYMENT_SUCCESS.equals(paymentStatus) || "DONE".equals(paymentStatus);
    }

    private WashPaymentRetryResponseDTO retryResponse(
            WashResponseDTO washHistory,
            String result,
            Integer balance,
            String message
    ) {
        return new WashPaymentRetryResponseDTO(
                washHistory.getWashHistoryId(),
                result,
                washHistory.getPaymentStatus(),
                washHistory.getAmount(),
                balance,
                null,
                message
        );
    }

    public List<WashResponseDTO>
    getNonMemberPendingWashHistory(
            String plateLast4
    ) {
        return washQueryService.getNonMemberPendingWashHistory(plateLast4);
    }

    public List<WashResponseDTO>
    getNonMemberWashHistoryList(
            String plateNumber
    ) {
        return washQueryService.getNonMemberWashHistoryList(plateNumber);
    }
}
