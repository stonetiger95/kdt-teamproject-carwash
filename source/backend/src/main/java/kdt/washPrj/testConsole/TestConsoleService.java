package kdt.washPrj.testConsole;

import kdt.washPrj.prepaid.PrepaidMapper;
import kdt.washPrj.prepaid.PrepaidService;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import kdt.washPrj.testConsole.dto.TestConsoleActionResponseDTO;
import kdt.washPrj.testConsole.dto.TestConsoleAutoPayRequestDTO;
import kdt.washPrj.testConsole.dto.TestConsoleChargeRequestDTO;
import kdt.washPrj.testConsole.dto.TestConsoleOnsitePaymentRequestDTO;
import kdt.washPrj.testConsole.dto.TestConsoleStateDTO;
import kdt.washPrj.testConsole.dto.TestConsoleWashTargetDTO;
import kdt.washPrj.testConsole.dto.TestConsoleWashCompletionRequestDTO;
import kdt.washPrj.wash.WashService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
public class TestConsoleService {

    private static final String SUCCESS = "SUCCESS";
    private static final String CANCELED = "CANCELED";
    private static final String DONE = "DONE";
    private static final Set<String> ONSITE_PAYMENT_TYPES = Set.of(
            "CASH",
            "CARD",
            "QR"
    );

    private final TestConsoleMapper testConsoleMapper;
    private final PrepaidMapper prepaidMapper;
    private final PrepaidService prepaidService;
    private final WashService washService;

    @Value("${app.test-console.enabled:false}")
    private boolean enabled;

    public TestConsoleService(
            TestConsoleMapper testConsoleMapper,
            PrepaidMapper prepaidMapper,
            PrepaidService prepaidService,
            WashService washService

    ) {
        this.testConsoleMapper = testConsoleMapper;
        this.prepaidMapper = prepaidMapper;
        this.prepaidService = prepaidService;
        this.washService = washService;
    }

    @Transactional
    public TestConsoleStateDTO getState(
            Long customerId,
            Long washHistoryId,
            Integer requiredAmount
    ) {
        requireEnabled();
        requireCustomer(customerId);

        if (requiredAmount != null && requiredAmount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "requiredAmount must be greater than 0."
            );
        }

        return loadState(
                customerId,
                washHistoryId,
                requiredAmount
        );
    }

    @Transactional
    public TestConsoleActionResponseDTO simulateCharge(
            TestConsoleChargeRequestDTO requestDTO
    ) {
        requireEnabled();

        if (requestDTO == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required.");
        }

        Long customerId = requestDTO.getCustomerId();
        Integer amount = requestDTO.getAmount();
        requireCustomer(customerId);
        requirePositiveAmount(amount);
        requirePrepaidSchemaReady(loadState(customerId, null, amount));

        String status = toResultStatus(requestDTO.getResult());
        Long paymentHistoryId = null;

        if (SUCCESS.equals(status)) {
            prepaidMapper.insertWalletIfAbsent(customerId);
            paymentHistoryId = prepaidMapper.reservePaymentHistoryId();
            prepaidMapper.insertSuccessfulTestChargePayment(
                    paymentHistoryId,
                    customerId,
                    amount,
                    "TEST_CHARGE"
            );
            prepaidService.completeChargePayment(paymentHistoryId, amount);
        }

        TestConsoleStateDTO state = loadState(
                customerId,
                null,
                null
        );

        return actionResponse(
                "TEST_CHARGE",
                status,
                SUCCESS.equals(status)
                        ? "테스트 충전이 지갑에 반영되었습니다."
                        : "테스트 충전 실패로 결제 이력을 생성하지 않았습니다.",
                customerId,
                null,
                paymentHistoryId,
                amount,
                state
        );
    }

    @Transactional
    public TestConsoleActionResponseDTO retryAutoPayment(
            TestConsoleAutoPayRequestDTO requestDTO
    ) {
        requireEnabled();

        if (requestDTO == null
                || requestDTO.getCustomerId() == null
                || requestDTO.getWashHistoryId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "customerId and washHistoryId are required."
            );
        }

        requireCustomer(requestDTO.getCustomerId());
        TestConsoleStateDTO beforePayment = loadState(
                requestDTO.getCustomerId(),
                requestDTO.getWashHistoryId(),
                null
        );
        requirePrepaidSchemaReady(beforePayment);

        if (Boolean.FALSE.equals(beforePayment.getEnoughBalance())
                && !Boolean.TRUE.equals(beforePayment.getBillingCardTableReady())) {
            return actionResponse(
                    "AUTO_PAYMENT",
                    "PAYMENT_REQUIRED",
                    "잔액이 부족하며 자동결제 카드 테이블이 준비되지 않았습니다.",
                    requestDTO.getCustomerId(),
                    requestDTO.getWashHistoryId(),
                    null,
                    beforePayment.getWashAmount(),
                    beforePayment
            );
        }

        PrepaidUseResponseDTO payment = prepaidService.autoUseWithBillingTopUp(
                requestDTO.getCustomerId(),
                requestDTO.getWashHistoryId()
        );
        TestConsoleStateDTO state = loadState(
                requestDTO.getCustomerId(),
                requestDTO.getWashHistoryId(),
                null
        );

        String result = payment == null ? "PAYMENT_REQUIRED" : SUCCESS;
        return actionResponse(
                "AUTO_PAYMENT",
                result,
                payment == null
                        ? "잔액이 부족하거나 사용 가능한 자동결제 카드가 없습니다."
                        : "자동결제와 세차 금액 차감이 완료되었습니다.",
                requestDTO.getCustomerId(),
                requestDTO.getWashHistoryId(),
                payment == null ? null : payment.getPaymentHistoryId(),
                payment == null ? state.getWashAmount() : payment.getAmount(),
                state
        );
    }

    @Transactional
    public TestConsoleActionResponseDTO simulateOnsitePayment(
            TestConsoleOnsitePaymentRequestDTO requestDTO
    ) {
        requireEnabled();

        if (requestDTO == null || requestDTO.getWashHistoryId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "washHistoryId is required."
            );
        }

        TestConsoleWashTargetDTO target = testConsoleMapper.selectWashTargetForUpdate(
                requestDTO.getWashHistoryId()
        );

        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wash history was not found.");
        }

        if (SUCCESS.equals(target.getPaymentStatus())
                || DONE.equals(target.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Wash history is already paid."
            );
        }

        Integer amount = target.getAmount();
        requirePositiveAmount(amount);

        if (requestDTO.getAmount() != null
                && !amount.equals(requestDTO.getAmount())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount must match the wash course price."
            );
        }

        String paymentType = normalizePaymentType(requestDTO.getPaymentType());
        String status = toResultStatus(requestDTO.getResult());

        Long paymentHistoryId = null;

        if (SUCCESS.equals(status)) {
            paymentHistoryId = testConsoleMapper.insertOnsitePayment(
                    target.getWashHistoryId(),
                    target.getCustomerId(),
                    amount,
                    paymentType,
                    status
            );
        }

        testConsoleMapper.updateWashPaymentStatus(
                target.getWashHistoryId(),
                status
        );

        TestConsoleStateDTO state = target.getCustomerId() == null
                ? null
                : loadState(
                        target.getCustomerId(),
                        target.getWashHistoryId(),
                        amount
                );

        return actionResponse(
                "ONSITE_PAYMENT",
                status,
                SUCCESS.equals(status)
                        ? "현장결제 성공이 세차내역에 반영되었습니다."
                        : "현장결제 실패로 결제 이력을 생성하지 않았습니다.",
                target.getCustomerId(),
                target.getWashHistoryId(),
                paymentHistoryId,
                amount,
                state
        );
    }

    private TestConsoleActionResponseDTO actionResponse(
            String action,
            String result,
            String message,
            Long customerId,
            Long washHistoryId,
            Long paymentHistoryId,
            Integer amount,
            TestConsoleStateDTO state
    ) {
        TestConsoleActionResponseDTO responseDTO = new TestConsoleActionResponseDTO();
        responseDTO.setAction(action);
        responseDTO.setResult(result);
        responseDTO.setMessage(message);
        responseDTO.setCustomerId(customerId);
        responseDTO.setWashHistoryId(washHistoryId);
        responseDTO.setPaymentHistoryId(paymentHistoryId);
        responseDTO.setAmount(amount);
        responseDTO.setBalance(state == null ? null : state.getBalance());
        responseDTO.setState(state);
        return responseDTO;
    }

    @Transactional
    public TestConsoleActionResponseDTO completeWash(
            TestConsoleWashCompletionRequestDTO requestDTO
    ) {
        requireEnabled();

        if (requestDTO == null
                || requestDTO.getWashHistoryId() == null
                || requestDTO.getWashHistoryId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "washHistoryId is required."
            );
        }

        Long washHistoryId = requestDTO.getWashHistoryId();

        TestConsoleWashTargetDTO target =
                testConsoleMapper.selectWashTargetForUpdate(
                        washHistoryId
                );

        if (target == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "세차내역을 찾을 수 없습니다."
            );
        }

        if ("WASH_DONE".equals(target.getWashStatus())) {
            return actionResponse(
                    "WASH_COMPLETE",
                    SUCCESS,
                    "이미 세차 완료 처리된 내역입니다.",
                    target.getCustomerId(),
                    washHistoryId,
                    null,
                    null,
                    null
            );
        }

        if (!"WASHING".equals(target.getWashStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "세차 중인 내역만 완료 처리할 수 있습니다."
            );
        }

        int updatedCount = washService.endWash(washHistoryId);

        if (updatedCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "세차 완료 처리에 실패했습니다."
            );
        }

        return actionResponse(
                "WASH_COMPLETE",
                SUCCESS,
                "세차 완료 처리되었습니다.",
                target.getCustomerId(),
                washHistoryId,
                null,
                null,
                null
        );
    }

    @Transactional
    public TestConsoleActionResponseDTO completeWalkInPayment(
            TestConsoleWashCompletionRequestDTO requestDTO
    ) {
        requireEnabled();

        if (requestDTO == null
                || requestDTO.getWashHistoryId() == null
                || requestDTO.getWashHistoryId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "washHistoryId is required."
            );
        }

        Long washHistoryId = requestDTO.getWashHistoryId();
        TestConsoleWashTargetDTO target =
                testConsoleMapper.selectWashTargetForUpdate(washHistoryId);

        if (target == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "세차내역을 찾을 수 없습니다."
            );
        }

        if (!testConsoleMapper.isNonMemberWalkIn(washHistoryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "비회원 비예약 세차내역만 시연용 결제 완료 처리가 가능합니다."
            );
        }

        if (SUCCESS.equals(target.getPaymentStatus())
                || DONE.equals(target.getPaymentStatus())) {
            return actionResponse(
                    "WALK_IN_PAYMENT_COMPLETE",
                    SUCCESS,
                    "이미 결제 완료 처리된 내역입니다.",
                    null,
                    washHistoryId,
                    null,
                    target.getAmount(),
                    null
            );
        }

        if (!"WAITING".equals(target.getPaymentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결제 대기 중인 세차내역만 완료 처리할 수 있습니다."
            );
        }

        Integer amount = target.getAmount();
        requirePositiveAmount(amount);

        Long paymentHistoryId = testConsoleMapper.insertOnsitePayment(
                washHistoryId,
                null,
                amount,
                "CARD",
                SUCCESS
        );
        int updated = testConsoleMapper.updateWashPaymentStatus(
                washHistoryId,
                SUCCESS
        );

        if (paymentHistoryId == null || updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결제 완료 처리에 실패했습니다."
            );
        }

        return actionResponse(
                "WALK_IN_PAYMENT_COMPLETE",
                SUCCESS,
                "비회원 비예약 차량의 결제가 완료 처리되었습니다.",
                null,
                washHistoryId,
                paymentHistoryId,
                amount,
                null
        );
    }

    @Transactional
    public TestConsoleActionResponseDTO cleanupAllTestVehicles() {
        requireEnabled();

        int cleaned = testConsoleMapper.cleanupAllVehicles();

        return actionResponse(
                "TEST_ALL_VEHICLES_CLEANUP",
                SUCCESS,
                cleaned == 0
                        ? "열려 있는 테스트 입차·세차 상태가 없습니다."
                        : "열려 있는 테스트 입차·세차 상태를 모두 종료했습니다.",
                null,
                null,
                null,
                cleaned,
                null
        );
    }

    private TestConsoleStateDTO loadState(
            Long customerId,
            Long washHistoryId,
            Integer requiredAmount
    ) {
        TestConsoleStateDTO state = testConsoleMapper.selectState(
                customerId,
                washHistoryId,
                requiredAmount
        );

        boolean prepaidSchemaReady = testConsoleMapper.isPrepaidSchemaReady();
        boolean billingCardTableReady = testConsoleMapper.hasBillingCardTable();

        state.setPrepaidSchemaReady(prepaidSchemaReady);
        state.setBillingCardTableReady(billingCardTableReady);

        if (billingCardTableReady) {
            state.setBillingCardRegistered(
                    testConsoleMapper.hasActiveBillingCard(customerId)
            );
            state.setAutoPayEnabled(
                    testConsoleMapper.hasAutoPayBillingCard(customerId)
            );
        } else {
            state.setBillingCardRegistered(false);
            state.setAutoPayEnabled(false);
        }

        return state;
    }

    private void requirePrepaidSchemaReady(TestConsoleStateDTO state) {
        if (!Boolean.TRUE.equals(state.getPrepaidSchemaReady())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Prepaid DB schema does not match the current backend mapper."
            );
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Test console is disabled."
            );
        }
    }

    private void requireCustomer(Long customerId) {
        if (customerId == null || !testConsoleMapper.existsCustomer(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer was not found.");
        }
    }

    private void requirePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "amount must be greater than 0."
            );
        }
    }

    private String toResultStatus(String result) {
        String normalized = result == null
                ? SUCCESS
                : result.trim().toUpperCase(Locale.ROOT);
        return SUCCESS.equals(normalized) ? SUCCESS : CANCELED;
    }

    private String normalizePaymentType(String paymentType) {
        String normalized = paymentType == null
                ? "QR"
                : paymentType.trim().toUpperCase(Locale.ROOT);

        if (!ONSITE_PAYMENT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "paymentType must be CASH, CARD, or QR."
            );
        }

        return normalized;
    }

}
