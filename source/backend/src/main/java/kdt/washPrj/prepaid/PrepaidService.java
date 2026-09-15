package kdt.washPrj.prepaid;

import kdt.washPrj.payment.PayAppClient;
import kdt.washPrj.payment.KakaoPayService;
import kdt.washPrj.payment.PaymentProviderRegistry;
import kdt.washPrj.payment.PaymentReconciliationService;
import kdt.washPrj.payment.TossPaymentsService;
import kdt.washPrj.payment.dto.KakaoPayReadyDTO;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.TossPaymentReadyDTO;
import kdt.washPrj.prepaid.dto.BillingCardDTO;
import kdt.washPrj.prepaid.dto.BillingCardAuthRequestDTO;
import kdt.washPrj.prepaid.dto.BillingCardPayAppReadyDTO;
import kdt.washPrj.prepaid.dto.BillingCardPayAppRequestDTO;
import kdt.washPrj.prepaid.dto.BillingCardRegisterRequestDTO;
import kdt.washPrj.prepaid.dto.BillingCardTestRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargeRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargePreparationDTO;
import kdt.washPrj.prepaid.dto.PrepaidChargeResponseDTO;
import kdt.washPrj.prepaid.dto.PrepaidUseRequestDTO;
import kdt.washPrj.prepaid.dto.PrepaidUseResponseDTO;
import kdt.washPrj.prepaid.dto.PrepaidWalletDTO;
import kdt.washPrj.prepaid.dto.PrepaidWashPaymentTargetDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PrepaidService {

    private static final Logger log = LoggerFactory.getLogger(PrepaidService.class);

    private static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final String PAYMENT_WAITING = "WAITING";
    private static final String PAYMENT_SUCCESS = "SUCCESS";
    private static final String PAYMENT_PROVIDER_PAYAPP = "PAYAPP";
    private static final String PAYMENT_TYPE_AUTO_BILLING = "AUTO_BILLING";
    private static final String PURPOSE_CHARGE = "CHARGE";
    private static final String BILLING_AUTH_SUCCESS = "SUCCESS";
    private static final String BILLING_AUTH_FAILED = "FAILED";
    private static final String DB_RESULT_OK = "OK";
    private static final String DB_RESULT_NOT_FOUND = "NOT_FOUND";
    private static final String DB_RESULT_FORBIDDEN = "FORBIDDEN";
    private static final String DB_RESULT_ALREADY_PAID = "ALREADY_PAID";
    private static final String DB_RESULT_INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    private static final String DB_RESULT_INVALID_REQUEST = "INVALID_REQUEST";

    private final PrepaidMapper prepaidMapper;
    private final PayAppClient payAppClient;
    private final PrepaidQueryService prepaidQueryService;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final PaymentReconciliationService paymentReconciliationService;
    private final KakaoPayService kakaoPayService;
    private final TossPaymentsService tossPaymentsService;
    private final Set<Long> activeChargeRequests =
            ConcurrentHashMap.newKeySet();

    @Value("${payapp.userid:}")
    private String payAppUserid;

    @Value("${payapp.open-pay-type:card}")
    private String openPayType;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.backend-base-url}")
    private String backendBaseUrl;

    @Value("${payapp.link-key:}")
    private String payAppLinkKey;

    @Value("${payapp.link-value:}")
    private String payAppLinkValue;

    @Value("${payapp.auto-billing.enabled:false}")
    private boolean payAppAutoBillingEnabled;

    @Value("${payapp.auto-billing.test-card-enabled:false}")
    private boolean payAppAutoBillingTestCardEnabled;

    @Value("${payment.reconciliation.stale-seconds:${PAYMENT_RECONCILIATION_STALE_SECONDS:1800}}")
    private int reconciliationStaleSeconds;

    @Value("${payapp.auto-billing.auth-amount:1000}")
    private Integer payAppBillingAuthAmount;

    public PrepaidService(
            PrepaidMapper prepaidMapper,
            PayAppClient payAppClient,
            PrepaidQueryService prepaidQueryService,
            PaymentProviderRegistry paymentProviderRegistry,
            PaymentReconciliationService paymentReconciliationService,
            KakaoPayService kakaoPayService,
            TossPaymentsService tossPaymentsService
    ) {
        this.prepaidMapper = prepaidMapper;
        this.payAppClient = payAppClient;
        this.prepaidQueryService = prepaidQueryService;
        this.paymentProviderRegistry = paymentProviderRegistry;
        this.paymentReconciliationService = paymentReconciliationService;
        this.kakaoPayService = kakaoPayService;
        this.tossPaymentsService = tossPaymentsService;
    }

    @Transactional
    public PrepaidWalletDTO getWallet(String loginId, String role) {
        requireCustomer(role);
        return prepaidQueryService.getWallet(loginId);
    }

    @Transactional
    public List<BillingCardDTO> getBillingCards(String loginId, String role) {
        return prepaidQueryService.getBillingCards(loginId, role);
    }

    @Transactional
    public BillingCardDTO registerBillingCard(
            BillingCardRegisterRequestDTO requestDTO,
            String loginId,
            String role
    ) {
        requireCustomer(role);

        if (requestDTO == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Billing card request is required.");
        }

        Long customerId = getCustomerId(loginId);

        boolean testCard = Boolean.TRUE.equals(requestDTO.getTestCard());

        if (testCard && !payAppAutoBillingTestCardEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Test billing cards are disabled."
            );
        }

        String payAppBillingKey = normalizeText(requestDTO.getPayAppBillingKey());
        String paymentProvider = normalizeText(requestDTO.getPaymentProvider());
        String cardName = normalizeText(requestDTO.getCardName());
        String cardNoMasked = normalizeText(requestDTO.getCardNoMasked());

        if (testCard) {
            payAppBillingKey = "TEST-PAYAPP-KEY-" + customerId + "-" + System.currentTimeMillis();
            paymentProvider = "TEST";
            cardName = firstNonBlank(cardName, "Test card");
            cardNoMasked = firstNonBlank(cardNoMasked, "0000-****-****-0000");
        } else {
            requireText(payAppBillingKey, "payAppBillingKey is required.");
            paymentProvider = firstNonBlank(paymentProvider, "PAYAPP");
            cardName = firstNonBlank(cardName, "Registered card");
        }

        Integer maxAutoPayAmount = requestDTO.getMaxAutoPayAmount();

        if (maxAutoPayAmount != null && maxAutoPayAmount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "maxAutoPayAmount must be greater than 0."
            );
        }

        boolean defaultCard = Boolean.TRUE.equals(requestDTO.getDefaultCard());
        boolean autoPayEnabled = !Boolean.FALSE.equals(requestDTO.getAutoPayEnabled());

        return saveBillingCard(
                customerId,
                payAppBillingKey,
                paymentProvider,
                cardName,
                cardNoMasked,
                defaultCard,
                autoPayEnabled,
                maxAutoPayAmount
        );
    }

    @Transactional
    public BillingCardDTO registerTestBillingCard(
            BillingCardTestRequestDTO requestDTO,
            String loginId,
            String role
    ) {
        requireCustomer(role);

        if (!payAppAutoBillingTestCardEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Test billing cards are disabled."
            );
        }

        BillingCardTestRequestDTO testRequest = requestDTO == null
                ? new BillingCardTestRequestDTO()
                : requestDTO;
        String cardName = firstNonBlank(
                normalizeText(testRequest.getCardName()),
                "Test card"
        );
        String cardLast4 = firstNonBlank(
                normalizeDigits(testRequest.getCardLast4()),
                "0000"
        );

        if (!cardLast4.matches("\\d{4}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cardLast4 must contain exactly 4 digits."
            );
        }

        BillingCardRegisterRequestDTO registerRequest =
                new BillingCardRegisterRequestDTO();
        registerRequest.setTestCard(true);
        registerRequest.setCardName(cardName);
        registerRequest.setCardNoMasked("0000-****-****-" + cardLast4);
        registerRequest.setDefaultCard(testRequest.getDefaultCard());
        registerRequest.setAutoPayEnabled(testRequest.getAutoPayEnabled());
        registerRequest.setMaxAutoPayAmount(testRequest.getMaxAutoPayAmount());

        return registerBillingCard(registerRequest, loginId, role);
    }

    @Transactional
    public BillingCardPayAppReadyDTO createPayAppBillingAuthRequest(
            BillingCardPayAppRequestDTO requestDTO,
            String loginId,
            String role
    ) {
        requireCustomer(role);

        validatePayAppBillingRequest(requestDTO);

        Long customerId = getCustomerId(loginId);
        requirePayAppSellerCredentials();

        int amount = payAppBillingAuthAmount == null || payAppBillingAuthAmount <= 0
                ? 1000
                : payAppBillingAuthAmount;
        String payAppRequestId = "PAYAPP-BILL-" + customerId + "-" + System.currentTimeMillis();

        Integer maxAutoPayAmount = requestDTO.getMaxAutoPayAmount();

        if (maxAutoPayAmount != null && maxAutoPayAmount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                "maxAutoPayAmount must be greater than 0."
            );
        }

        String recvPhone = normalizeText(requestDTO.getRecvPhone());
        recvPhone = firstNonBlank(
                recvPhone,
                normalizeText(prepaidMapper.selectCustomerPhoneByCustomerId(customerId))
        );
        requireText(recvPhone, "recvPhone is required.");

        boolean defaultCard = !Boolean.FALSE.equals(requestDTO.getDefaultCard());
        boolean autoPayEnabled = !Boolean.FALSE.equals(requestDTO.getAutoPayEnabled());

        prepaidMapper.insertBillingAuthRequest(
                customerId,
                payAppRequestId,
                amount,
                defaultCard,
                autoPayEnabled,
                maxAutoPayAmount
        );

        String cardNo = normalizeDigits(requestDTO.getCardNo());
        String expMonth = normalizeDigits(requestDTO.getExpMonth());
        String expYear = normalizeDigits(requestDTO.getExpYear());
        String buyerAuthNo = normalizeDigits(requestDTO.getBuyerAuthNo());
        String cardPw = normalizeDigits(requestDTO.getCardPw());
        String buyerName = normalizeText(requestDTO.getBuyerName());

        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "billRegist");
        form.put("userid", payAppUserid);
        form.put("linkkey", payAppLinkKey);
        form.put("cardNo", cardNo);
        form.put("expMonth", expMonth);
        form.put("expYear", expYear);
        form.put("buyerAuthNo", buyerAuthNo);
        form.put("cardPw", cardPw);
        form.put("buyerPhone", recvPhone);
        form.put("buyerName", buyerName);
        form.put("buyerId", String.valueOf(customerId));

        Map<String, String> payAppResponse;

        try {
            payAppResponse = payAppClient.postForm(form);
        } catch (ResponseStatusException e) {
            prepaidMapper.updateBillingAuthRequestResult(
                    payAppRequestId,
                    BILLING_AUTH_FAILED,
                    "PAYAPP_REQUEST_FAILED",
                    e.getReason(),
                    null,
                    null
            );
            throw e;
        }

        if (!"1".equals(payAppResponse.get("state"))) {
            prepaidMapper.updateBillingAuthRequestResult(
                    payAppRequestId,
                    BILLING_AUTH_FAILED,
                    payAppResponse.get("state"),
                    payAppResponse.getOrDefault("errorMessage", "PayApp billing auth request failed."),
                    null,
                    null
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    payAppResponse.getOrDefault("errorMessage", "PayApp billing auth request failed.")
            );
        }

        String payAppBillingKey = firstNonBlank(
                payAppResponse.get("encBill"),
                payAppResponse.get("encbill")
        );

        if (payAppBillingKey == null || payAppBillingKey.isBlank()) {
            prepaidMapper.updateBillingAuthRequestResult(
                    payAppRequestId,
                    BILLING_AUTH_FAILED,
                    "MISSING_ENC_BILL",
                    "PayApp did not return an encBill.",
                    null,
                    null
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayApp did not return an encBill."
            );
        }

        saveBillingCard(
                customerId,
                payAppBillingKey,
                "PAYAPP",
                firstNonBlank(payAppResponse.get("cardname"), "PayApp card"),
                firstNonBlank(payAppResponse.get("cardno"), maskCardNumber(cardNo)),
                defaultCard,
                autoPayEnabled,
                maxAutoPayAmount
        );

        prepaidMapper.updateBillingAuthRequestResult(
                payAppRequestId,
                BILLING_AUTH_SUCCESS,
                payAppResponse.getOrDefault("errno", "00000"),
                "PayApp billing card was registered.",
                payAppBillingKey,
                null
        );

        BillingCardPayAppReadyDTO responseDTO = new BillingCardPayAppReadyDTO();
        responseDTO.setStatus(BILLING_AUTH_SUCCESS);
        responseDTO.setMessage("PayApp billing card was registered.");
        return responseDTO;
    }

    @Transactional
    public String completePayAppBillingAuth(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return billingResultPage(false, "PayApp billing auth response is empty.");
        }

        if (!isValidPayAppFeedback(params)) {
            return billingResultPage(false, "PayApp billing auth validation failed.");
        }

        String payAppRequestId = firstNonBlank(params.get("var1"), params.get("payapp_request_id"));
        String payAppTransactionId = firstNonBlank(params.get("mul_no"), params.get("payapp_transaction_id"));
        String resultCode = params.get("pay_state");
        String resultMessage = firstNonBlank(
                params.get("pay_memo"),
                params.get("errorMessage")
        );
        Integer responseAmount = parseInteger(params.get("price"));

        if (payAppRequestId == null || payAppRequestId.isBlank()) {
            return billingResultPage(false, "PayApp billing auth id is missing.");
        }

        BillingCardAuthRequestDTO authRequest =
                prepaidMapper.selectBillingAuthRequestForUpdate(payAppRequestId);

        if (authRequest == null) {
            return billingResultPage(false, "Billing auth request was not found.");
        }

        if (!"WAITING".equals(authRequest.getStatus())) {
            return billingResultPage(
                    BILLING_AUTH_SUCCESS.equals(authRequest.getStatus()),
                    authRequest.getResultMessage()
            );
        }

        if (responseAmount == null || !responseAmount.equals(authRequest.getAmount())) {
            prepaidMapper.updateBillingAuthRequestResult(
                    payAppRequestId,
                    BILLING_AUTH_FAILED,
                    "INVALID_AMOUNT",
                    "PayApp billing auth amount does not match.",
                    null,
                    payAppTransactionId
            );
            return billingResultPage(false, "PayApp billing auth amount does not match.");
        }

        if (!isPayAppPaymentSuccess(resultCode)) {
            prepaidMapper.updateBillingAuthRequestResult(
                    payAppRequestId,
                    BILLING_AUTH_FAILED,
                    resultCode,
                    firstNonBlank(resultMessage, "PayApp billing auth failed."),
                    null,
                    payAppTransactionId
            );
            return billingResultPage(false, firstNonBlank(resultMessage, "PayApp billing auth failed."));
        }

        String payAppBillingKey = firstNonBlank(
                params.get("payapp_billing_key"),
                firstNonBlank(params.get("billing_key"), params.get("billingKey"))
        );

        if (payAppBillingKey == null || payAppBillingKey.isBlank()) {
            prepaidMapper.updateBillingAuthRequestResult(
                    payAppRequestId,
                    BILLING_AUTH_FAILED,
                    "MISSING_BILLING_TOKEN",
                    "PayApp payment succeeded, but billing token was not included.",
                    null,
                    payAppTransactionId
            );
            return billingResultPage(false, "PayApp billing token was not included.");
        }

        saveBillingCard(
                authRequest.getCustomerId(),
                payAppBillingKey,
                "PAYAPP",
                firstNonBlank(params.get("card_name"), "PayApp card"),
                firstNonBlank(params.get("card_no"), params.get("cardNoMasked")),
                Boolean.TRUE.equals(authRequest.getDefaultCard()),
                !Boolean.FALSE.equals(authRequest.getAutoPayEnabled()),
                authRequest.getMaxAutoPayAmount()
        );

        prepaidMapper.updateBillingAuthRequestResult(
                payAppRequestId,
                BILLING_AUTH_SUCCESS,
                resultCode,
                firstNonBlank(resultMessage, "PayApp billing card was registered."),
                payAppBillingKey,
                payAppTransactionId
        );

        return billingResultPage(true, "PayApp billing card was registered.");
    }

    @Transactional
    public void deleteBillingCard(Long payAppCardId, String loginId, String role) {
        requireCustomer(role);

        if (payAppCardId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payAppCardId is required.");
        }

        Long customerId = getCustomerId(loginId);
        int updatedCount = prepaidMapper.deleteBillingCard(customerId, payAppCardId);

        if (updatedCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Billing card was not found.");
        }
    }

    public PrepaidChargeResponseDTO charge(
            PrepaidChargeRequestDTO requestDTO,
            String loginId,
            String role
    ) {
        requireCustomer(role);
        validateAmount(requestDTO == null ? null : requestDTO.getAmount());
        requireText(requestDTO.getRecvPhone(), "recvPhone is required.");

        String paymentProvider = paymentProviderRegistry.requireAvailable(
                requestDTO.getPaymentProvider()
        );
        PrepaidChargePreparationDTO preparation = prepareChargeRequest(
                loginId,
                paymentProvider,
                requestDTO.getAmount()
        );
        boolean resumeExistingRequest = preparation != null
                && "ALREADY_WAITING".equals(preparation.getResultCode())
                && paymentReconciliationService.isResumableWaitingPayment(
                        preparation.getPaymentHistoryId(),
                        paymentProvider,
                        requestDTO.getAmount()
                );
        if (!resumeExistingRequest) {
            requireChargePreparation(preparation);
        }

        Long customerId = preparation.getCustomerId();
        if (!activeChargeRequests.add(customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A prepaid charge request is already in progress."
            );
        }

        try {
            return requestCharge(requestDTO, preparation, paymentProvider);
        } finally {
            activeChargeRequests.remove(customerId);
        }
    }

    public PaymentResponseDTO abandonCharge(
            Long paymentHistoryId,
            String loginId,
            String role
    ) {
        requireCustomer(role);
        if (paymentHistoryId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "paymentHistoryId is required."
            );
        }

        PaymentResponseDTO payment = prepaidMapper.selectAuthorizedPaymentById(
                loginId,
                paymentHistoryId
        );
        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Prepaid charge payment was not found."
            );
        }
        if (!PURPOSE_CHARGE.equals(payment.getPurpose())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only prepaid charge payments can be abandoned."
            );
        }

        if (PAYMENT_WAITING.equals(payment.getStatus())) {
            paymentReconciliationService.abandonWaitingPaymentForRetry(
                    paymentHistoryId
            );
        }

        return prepaidMapper.selectAuthorizedPaymentById(
                loginId,
                paymentHistoryId
        );
    }

    private PrepaidChargeResponseDTO requestCharge(
            PrepaidChargeRequestDTO requestDTO,
            PrepaidChargePreparationDTO preparation,
            String paymentProvider
    ) {
        Long customerId = preparation.getCustomerId();
        Long paymentHistoryId = preparation.getPaymentHistoryId();

        if (PaymentProviderRegistry.KAKAOPAY.equals(paymentProvider)) {
            try {
                KakaoPayReadyDTO ready = kakaoPayService.readyCharge(
                        paymentHistoryId,
                        customerId,
                        requestDTO.getOrderName(),
                        requestDTO.getAmount(),
                        requestDTO.getReturnClient()
                );
                PrepaidChargeResponseDTO response = baseChargeResponse(
                        requestDTO,
                        preparation,
                        paymentProvider
                );
                response.setPayUrl(ready.getPayUrl());
                response.setQrUrl(ready.getQrUrl());
                response.setAppUrl(ready.getAppUrl());
                return response;
            } catch (RuntimeException exception) {
                markChargeRequestFailed(
                        paymentHistoryId,
                        paymentProvider,
                        "REQUEST_EXCEPTION",
                        exception.getMessage()
                );
                throw exception;
            }
        }

        if (PaymentProviderRegistry.TOSS_PAYMENTS.equals(paymentProvider)) {
            try {
                TossPaymentReadyDTO ready = tossPaymentsService.ready(
                        paymentHistoryId,
                        requestDTO.getReturnClient()
                );
                PrepaidChargeResponseDTO response = baseChargeResponse(
                        requestDTO,
                        preparation,
                        paymentProvider
                );
                response.setProviderClientKey(ready.getClientKey());
                response.setOrderId(ready.getOrderId());
                response.setSuccessUrl(ready.getSuccessUrl());
                response.setFailUrl(ready.getFailUrl());
                response.setPayUrl(tossPaymentsService.checkoutUrl(
                        ready,
                        requestDTO.getOrderName(),
                        requestDTO.getAmount()
                ));
                return response;
            } catch (RuntimeException exception) {
                markChargeRequestFailed(
                        paymentHistoryId,
                        paymentProvider,
                        "REQUEST_EXCEPTION",
                        exception.getMessage()
                );
                throw exception;
            }
        }

        Map<String, String> payAppResponse;
        try {
            payAppResponse = requestPayApp(
                    paymentHistoryId,
                    customerId,
                    requestDTO
            );
        } catch (RuntimeException exception) {
            markChargeRequestFailed(
                    paymentHistoryId,
                    paymentProvider,
                    "REQUEST_EXCEPTION",
                    exception.getMessage()
            );
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "PayApp charge request failed."
            );
        }

        if (!"1".equals(payAppResponse.get("state"))) {
            markChargeRequestFailed(
                    paymentHistoryId,
                    paymentProvider,
                    payAppResponse.get("state"),
                    payAppResponse.get("errorMessage")
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    payAppResponse.getOrDefault("errorMessage", "PayApp charge request failed.")
            );
        }

        recordChargeProviderRequest(
                paymentHistoryId,
                payAppResponse.get("mul_no"),
                "REQUEST_ACCEPTED"
        );

        PrepaidChargeResponseDTO responseDTO = baseChargeResponse(
                requestDTO,
                preparation,
                paymentProvider
        );
        responseDTO.setPayUrl(payAppResponse.get("payurl"));
        responseDTO.setQrUrl(payAppResponse.get("qrurl"));
        return responseDTO;
    }

    private PrepaidChargePreparationDTO prepareChargeRequest(
            String loginId,
            String paymentProvider,
            Integer amount
    ) {
        PrepaidChargePreparationDTO preparation =
                prepaidMapper.prepareChargeRequest(
                        loginId,
                        paymentProvider,
                        amount
                );

        if (preparation != null
                && "ALREADY_WAITING".equals(preparation.getResultCode())) {
            boolean existingRequestClosed =
                    paymentReconciliationService.abandonWaitingPaymentForRetry(
                            preparation.getPaymentHistoryId()
                    )
                    || paymentReconciliationService.reconcileStalePaymentNow(
                            preparation.getPaymentHistoryId()
                    );
            if (existingRequestClosed) {
                return prepaidMapper.prepareChargeRequest(
                        loginId,
                        paymentProvider,
                        amount
                );
            }
        }

        return preparation;
    }

    private PrepaidChargeResponseDTO baseChargeResponse(
            PrepaidChargeRequestDTO requestDTO,
            PrepaidChargePreparationDTO preparation,
            String paymentProvider
    ) {
        PrepaidChargeResponseDTO responseDTO = new PrepaidChargeResponseDTO();
        responseDTO.setPaymentHistoryId(preparation.getPaymentHistoryId());
        responseDTO.setWalletId(preparation.getWalletId());
        responseDTO.setCustomerId(preparation.getCustomerId());
        responseDTO.setAmount(requestDTO.getAmount());
        responseDTO.setBalance(preparation.getBalance());
        responseDTO.setStatus(PAYMENT_WAITING);
        responseDTO.setPaymentProvider(paymentProvider);
        return responseDTO;
    }

    private void recordChargeProviderRequest(
            Long paymentHistoryId,
            String providerTransactionId,
            String providerStatus
    ) {
        try {
            String result = prepaidMapper.recordPaymentProviderRequest(
                    paymentHistoryId,
                    PAYMENT_PROVIDER_PAYAPP,
                    providerTransactionId,
                    providerStatus,
                    reconciliationStaleSeconds
            );
            if (!"RECORDED".equals(result) && !"ALREADY_FINAL".equals(result)) {
                log.warn(
                        "Charge provider request requires review. paymentHistoryId={}, result={}",
                        paymentHistoryId,
                        result
                );
            }
        } catch (RuntimeException exception) {
            // The provider accepted the request, so keep it pending for reconciliation.
            log.error(
                    "Charge provider request id persistence failed. paymentHistoryId={}",
                    paymentHistoryId,
                    exception
            );
        }
    }

    @Transactional
    public void completeChargePayment(Long paymentHistoryId, Integer feedbackAmount) {
        int result = prepaidMapper.applyConfirmedCharge(
                paymentHistoryId,
                feedbackAmount
        );
        if (result < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Charge amount does not match."
            );
        }
    }

    @Transactional
    public PrepaidUseResponseDTO use(
            PrepaidUseRequestDTO requestDTO,
            String loginId,
            String role
    ) {
        requireCustomer(role);

        if (requestDTO == null || requestDTO.getWashHistoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "washHistoryId is required.");
        }

        return requireSuccessfulPrepaidUse(prepaidMapper.executeMyPrepaidWashPayment(
                loginId,
                requestDTO.getWashHistoryId()
        ));
    }

    @Transactional
    public PrepaidUseResponseDTO autoUseWithBillingTopUp(
            Long customerId,
            Long washHistoryId
    ) {
        log.info(
                "[AUTO_USE] customerId={}, washHistoryId={}",
                customerId,
                washHistoryId
        );
        if (customerId == null || washHistoryId == null) {
            log.info("[AUTO_USE_FAIL] missing id. customerId={}, washHistoryId={}", customerId, washHistoryId);
            return null;
        }

        prepaidMapper.insertWalletIfAbsent(customerId);

        PrepaidWalletDTO wallet = prepaidMapper.selectWalletForUpdate(customerId);
        PrepaidWashPaymentTargetDTO target =
                prepaidMapper.selectWashPaymentTargetForUpdate(
                        customerId,
                        washHistoryId
                );
        log.info(
                "[AUTO_USE_TARGET] walletId={}, balance={}, washAmount={}, paymentStatus={}",
                wallet == null ? null : wallet.getWalletId(),
                wallet == null ? null : wallet.getBalance(),
                target == null ? null : target.getAmount(),
                target == null ? null : target.getPaymentStatus()
        );

        if (!isAutoPayableTarget(target)) {
            log.info("[AUTO_USE_FAIL] target is not payable");
            return null;
        }

        if (!hasEnoughBalance(wallet, target)) {
            int shortage = target.getAmount() - wallet.getBalance();
            log.info("[AUTO_CHARGE_REQUIRED] shortage={}", shortage);
            if (!autoChargeShortage(customerId, wallet.getWalletId(), shortage, target)) {
                prepaidMapper.releaseWashBenefitReservation(washHistoryId);
                log.info("[AUTO_USE_FAIL] auto charge failed. customerId={}, washHistoryId={}, shortage={}",
                        customerId, washHistoryId, shortage);
                return null;
            }

            wallet = prepaidMapper.selectWalletForUpdate(customerId);
            log.info("[AUTO_CHARGE_BALANCE] customerId={}, balanceAfterCharge={}",
                    customerId, wallet == null ? null : wallet.getBalance());
        }

        if (!hasEnoughBalance(wallet, target)) {
            log.info("[AUTO_USE_FAIL] balance is still insufficient. customerId={}, balance={}, amount={}",
                    customerId,
                    wallet == null ? null : wallet.getBalance(),
                    target == null ? null : target.getAmount());
            return null;
        }

        log.info("[AUTO_USE_DEBIT_START] customerId={}, washHistoryId={}, amount={}",
                customerId, washHistoryId, target.getAmount());
        return useWalletForWash(customerId, target);
    }

    @Transactional
    public PrepaidUseResponseDTO useAfterConfirmedAutoCharge(
            Long customerId,
            Long washHistoryId
    ) {
        if (customerId == null || washHistoryId == null) {
            return null;
        }

        PrepaidUseResponseDTO responseDTO =
                prepaidMapper.executePrepaidWashPayment(customerId, washHistoryId);
        if (!isSuccessfulPrepaidUse(responseDTO)) {
            log.warn(
                    "Confirmed auto charge could not be used. customerId={}, washHistoryId={}, resultCode={}",
                    customerId,
                    washHistoryId,
                    responseDTO == null ? null : responseDTO.getResultCode()
            );
            return null;
        }

        return responseDTO;
    }

    private PrepaidUseResponseDTO useWalletForWash(
            Long customerId,
            PrepaidWashPaymentTargetDTO target
    ) {
        PrepaidUseResponseDTO responseDTO = prepaidMapper.executePrepaidWashPayment(
                customerId,
                target.getWashHistoryId()
        );
        if (!isSuccessfulPrepaidUse(responseDTO)) {
            log.warn("[AUTO_USE_FAIL] customerId={}, washHistoryId={}, resultCode={}",
                    customerId,
                    target.getWashHistoryId(),
                    responseDTO == null ? null : responseDTO.getResultCode());
            return null;
        }
        log.info("[AUTO_USE_SUCCESS] customerId={}, washHistoryId={}, amount={}, balanceAfter={}",
                customerId,
                target.getWashHistoryId(),
                responseDTO.getAmount(),
                responseDTO.getBalance());
        return responseDTO;
    }

    private void markChargeRequestFailed(
            Long paymentHistoryId,
            String paymentProvider,
            String providerStatus,
            String failureReason
    ) {
        try {
            int updated = prepaidMapper.markPaymentRequestFailed(
                    paymentHistoryId,
                    paymentProvider,
                    providerStatus,
                    failureReason
            );
            if (updated != 1) {
                log.warn(
                        "Charge request failure was not persisted. paymentHistoryId={}, providerStatus={}",
                        paymentHistoryId,
                        providerStatus
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Charge request failure persistence failed. paymentHistoryId={}",
                    paymentHistoryId,
                    exception
            );
        }
    }

    private void requireChargePreparation(
            PrepaidChargePreparationDTO preparation
    ) {
        if (preparation == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer was not found."
            );
        }
        if ("FORBIDDEN".equals(preparation.getResultCode())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer authority is required."
            );
        }
        if ("INVALID_REQUEST".equals(preparation.getResultCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid prepaid charge request."
            );
        }
        if ("ALREADY_WAITING".equals(preparation.getResultCode())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "기존 결제가 아직 처리 중입니다. 결제창에서 취소하거나 결제 유효시간이 지난 후 다시 시도해주세요."
            );
        }
        if (!"OK".equals(preparation.getResultCode())
                || preparation.getCustomerId() == null
                || preparation.getPaymentHistoryId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Prepaid charge request could not be prepared."
            );
        }
    }

    private PrepaidUseResponseDTO requireSuccessfulPrepaidUse(PrepaidUseResponseDTO responseDTO) {
        String resultCode = responseDTO == null ? null : responseDTO.getResultCode();
        if (DB_RESULT_OK.equals(resultCode)) {
            return responseDTO;
        }

        if (DB_RESULT_NOT_FOUND.equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wash history was not found.");
        }
        if (DB_RESULT_FORBIDDEN.equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer authority is required.");
        }
        if (DB_RESULT_ALREADY_PAID.equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wash history is already paid.");
        }
        if (DB_RESULT_INSUFFICIENT_BALANCE.equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prepaid balance is not enough.");
        }
        if (DB_RESULT_INVALID_REQUEST.equals(resultCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid prepaid wash payment request.");
        }
        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Prepaid wash payment could not be completed."
        );
    }

    private boolean isSuccessfulPrepaidUse(PrepaidUseResponseDTO responseDTO) {
        return responseDTO != null && DB_RESULT_OK.equals(responseDTO.getResultCode());
    }

    private boolean isAutoPayableTarget(PrepaidWashPaymentTargetDTO target) {
        if (target == null || PAYMENT_SUCCESS.equals(target.getPaymentStatus())) {
            return false;
        }

        return target.getAmount() != null && target.getAmount() >= 0;
    }

    private boolean hasEnoughBalance(
            PrepaidWalletDTO wallet,
            PrepaidWashPaymentTargetDTO target
    ) {
        return wallet != null
                && wallet.getBalance() != null
                && target != null
                && target.getAmount() != null
                && wallet.getBalance() >= target.getAmount();
    }

    private boolean autoChargeShortage(
            Long customerId,
            Long walletId,
            Integer shortage,
            PrepaidWashPaymentTargetDTO target
    ) {
        if (shortage == null || shortage <= 0) {
            return true;
        }

        BillingCardDTO billingCard =
                prepaidMapper.selectDefaultBillingCardForUpdate(customerId);

        log.info("[BILLING_CARD] customerId={}, exists={}, provider={}, billingKeyExists={}, maxAutoPayAmount={}",
                customerId,
                billingCard != null,
                billingCard == null ? null : billingCard.getPaymentProvider(),
                billingCard != null
                        && billingCard.getPayAppBillingKey() != null
                        && !billingCard.getPayAppBillingKey().isBlank(),
                billingCard == null ? null : billingCard.getMaxAutoPayAmount());

        if (billingCard == null) {
            log.info("[AUTO_CHARGE_FAIL] billing card not found. customerId={}", customerId);
            return false;
        }

        if (billingCard.getPayAppBillingKey() == null
                || billingCard.getPayAppBillingKey().isBlank()) {
            log.info("[AUTO_CHARGE_FAIL] billing key is empty. customerId={}", customerId);
            return false;
        }

        if (billingCard.getMaxAutoPayAmount() != null
                && shortage > billingCard.getMaxAutoPayAmount()) {
            log.info("[AUTO_CHARGE_FAIL] shortage exceeds limit. customerId={}, shortage={}, limit={}",
                    customerId, shortage, billingCard.getMaxAutoPayAmount());
            return false;
        }

        Long paymentHistoryId = prepaidMapper.reservePaymentHistoryId();

        boolean approved = approvePayAppAutoBilling(
                billingCard,
                paymentHistoryId,
                customerId,
                shortage,
                target.getOrderName(),
                target.getWashHistoryId(),
                prepaidMapper.selectCustomerPhoneByCustomerId(customerId)
        );
        log.info("[AUTO_BILLING_RESULT] paymentHistoryId={}, approved={}", paymentHistoryId, approved);

        if (!approved) {
            return false;
        }

        if (!"TEST".equalsIgnoreCase(billingCard.getPaymentProvider())) {
            log.info(
                    "PayApp auto billing request accepted and is waiting for feedback. paymentHistoryId={}, washHistoryId={}, amount={}",
                    paymentHistoryId,
                    target.getWashHistoryId(),
                    shortage
            );
            return true;
        }

        prepaidMapper.insertSuccessfulTestChargePayment(
                paymentHistoryId,
                customerId,
                shortage,
                PAYMENT_TYPE_AUTO_BILLING
        );
        completeChargePayment(paymentHistoryId, shortage);
        log.info("[AUTO_CHARGE_SUCCESS] paymentHistoryId={}, walletId={}, amount={}",
                paymentHistoryId, walletId, shortage);

        return true;
    }

    private boolean approvePayAppAutoBilling(
            BillingCardDTO billingCard,
            Long paymentHistoryId,
            Long customerId,
            Integer amount,
            String orderName,
            Long washHistoryId,
            String recvPhone
    ) {
        log.info("[AUTO_BILLING_CONFIG] paymentHistoryId={}, provider={}, amount={}, enabled={}, testCardEnabled={}",
                paymentHistoryId,
                billingCard.getPaymentProvider(),
                amount,
                payAppAutoBillingEnabled,
                payAppAutoBillingTestCardEnabled);

        if ("TEST".equalsIgnoreCase(billingCard.getPaymentProvider())) {
            return payAppAutoBillingTestCardEnabled;
        }

        if (!payAppAutoBillingEnabled) {
            log.warn(
                    "PayApp auto billing is disabled. paymentHistoryId={}, provider={}",
                    paymentHistoryId,
                    billingCard.getPaymentProvider()
            );
            return false;
        }

        requirePayAppSellerCredentials();
        requireText(recvPhone, "Customer phone is required for PayApp auto billing.");

        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "billPay");
        form.put("userid", payAppUserid);
        form.put("linkkey", payAppLinkKey);
        form.put("encBill", billingCard.getPayAppBillingKey());
        form.put("goodname", firstNonBlank(orderName, "Wash prepaid auto charge"));
        form.put("price", String.valueOf(amount));
        form.put("recvphone", recvPhone);
        form.put("amount_taxable", String.valueOf(amount));
        form.put("amount_taxfree", "0");
        form.put("amount_vat", "0");
        form.put("feedbackurl", backendBaseUrl + "/api/payments/payapp/feedback");
        form.put("var1", String.valueOf(paymentHistoryId));
        form.put(
                "var2",
                "AUTO_WASH:" + customerId + ":" + washHistoryId + ":" + amount
        );
        form.put("checkretry", "y");

        Map<String, String> response = payAppClient.postForm(form);
        String state = response.get("state");

        if (!"1".equals(state)) {
            log.warn(
                    "PayApp auto billing request was rejected. paymentHistoryId={}, state={}, message={}",
                    paymentHistoryId,
                    state,
                    response.get("errorMessage")
            );
            return false;
        }

        return true;
    }

    private boolean isValidPayAppFeedback(Map<String, String> params) {
        if (!hasPayAppFeedbackCredentials()) {
            log.error("PayApp feedback credentials are not configured.");
            return false;
        }

        return secureEquals(payAppUserid, params.get("userid"))
                && secureEquals(payAppLinkKey, params.get("linkkey"))
                && secureEquals(payAppLinkValue, params.get("linkval"));
    }

    private boolean hasPayAppFeedbackCredentials() {
        return payAppUserid != null && !payAppUserid.isBlank()
                && payAppLinkKey != null && !payAppLinkKey.isBlank()
                && payAppLinkValue != null && !payAppLinkValue.isBlank();
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isPayAppPaymentSuccess(String payState) {
        return "4".equals(payState);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        String suffix = path == null ? "" : path.trim();

        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base.substring(0, base.length() - 1) + suffix;
        }

        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }

        return base + suffix;
    }

    private String billingResultPage(boolean success, String message) {
        String result = success ? "success" : "fail";
        String targetUrl = joinUrl(
                frontendBaseUrl,
                "/customer/dashboard?billingCard=" + result
        );
        String safeMessage = escapeHtml(firstNonBlank(message, ""));
        String safeTargetUrl = escapeJson(targetUrl);

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                    <meta charset="utf-8">
                    <title>PayApp billing card result</title>
                </head>
                <body>
                    <p>%s</p>
                    <script>
                        window.location.replace("%s");
                    </script>
                </body>
                </html>
                """.formatted(safeMessage, safeTargetUrl);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        return second;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void validatePayAppBillingRequest(BillingCardPayAppRequestDTO requestDTO) {
        if (requestDTO == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Card information is required."
            );
        }

        String cardNo = normalizeDigits(requestDTO.getCardNo());
        String expMonth = normalizeDigits(requestDTO.getExpMonth());
        String expYear = normalizeDigits(requestDTO.getExpYear());
        String buyerAuthNo = normalizeDigits(requestDTO.getBuyerAuthNo());
        String cardPw = normalizeDigits(requestDTO.getCardPw());

        if (cardNo == null || cardNo.length() < 12 || cardNo.length() > 19) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardNo is invalid.");
        }

        if (expMonth == null || !expMonth.matches("(0[1-9]|1[0-2])")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expMonth is invalid.");
        }

        if (expYear == null || !expYear.matches("\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expYear is invalid.");
        }

        if (buyerAuthNo == null
                || !(buyerAuthNo.matches("\\d{6}") || buyerAuthNo.matches("\\d{10}"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerAuthNo is invalid.");
        }

        if (cardPw == null || !cardPw.matches("\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cardPw is invalid.");
        }

        requireText(requestDTO.getBuyerName(), "buyerName is required.");
    }

    private void requirePayAppSellerCredentials() {
        if (payAppUserid == null || payAppUserid.isBlank()
                || payAppLinkKey == null || payAppLinkKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PayApp seller credentials are not configured."
            );
        }
    }

    private String normalizeDigits(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.replaceAll("\\D", "");
    }

    private String maskCardNumber(String cardNo) {
        if (cardNo == null || cardNo.length() < 8) {
            return "****";
        }

        return cardNo.substring(0, 4)
                + "-****-****-"
                + cardNo.substring(cardNo.length() - 4);
    }

    private BillingCardDTO saveBillingCard(
            Long customerId,
            String payAppBillingKey,
            String paymentProvider,
            String cardName,
            String cardNoMasked,
            boolean defaultCard,
            boolean autoPayEnabled,
            Integer maxAutoPayAmount
    ) {
        BillingCardDTO billingCard = prepaidMapper.registerBillingCard(
                customerId,
                payAppBillingKey,
                paymentProvider,
                cardName,
                cardNoMasked,
                defaultCard,
                autoPayEnabled,
                maxAutoPayAmount
        );

        if (billingCard == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Billing card was not saved."
            );
        }
        return billingCard;
    }

    private Long getCustomerId(String loginId) {
        Long customerId = prepaidMapper.selectCustomerIdByLoginId(loginId);

        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account is required.");
        }

        return customerId;
    }

    private void validateAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than 0.");
        }
    }

    private Map<String, String> requestPayApp(
            Long paymentHistoryId,
            Long customerId,
            PrepaidChargeRequestDTO requestDTO
    ) {
        String orderName = requestDTO.getOrderName();

        if (orderName == null || orderName.isBlank()) {
            orderName = "Prepaid point charge";
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("cmd", "payrequest");
        form.put("userid", payAppUserid);
        form.put("goodname", orderName);
        form.put("price", String.valueOf(requestDTO.getAmount()));
        form.put("recvphone", requestDTO.getRecvPhone());
        form.put("memo", "Prepaid point charge");
        form.put("reqaddr", "0");
        form.put("feedbackurl", backendBaseUrl + "/api/payments/payapp/feedback");
        form.put("var1", String.valueOf(paymentHistoryId));
        form.put(
                "var2",
                PURPOSE_CHARGE + ":" + customerId + ":" + requestDTO.getAmount()
        );
        form.put("smsuse", "n");
        form.put(
                "returnurl",
                backendBaseUrl
                        + "/api/public/payments/payapp/return/customer?paymentId="
                        + paymentHistoryId
                        + returnClientQuery(requestDTO.getReturnClient())
        );
        form.put("skip_cstpage", "y");
        form.put("openpaytype", openPayType);
        form.put("checkretry", "y");

        return payAppClient.postForm(form);
    }

    private String returnClientQuery(String returnClient) {
        if ("FLUTTER_WEB".equalsIgnoreCase(returnClient)) {
            return "&returnClient=FLUTTER_WEB";
        }
        if ("MOBILE".equalsIgnoreCase(returnClient)) {
            return "&returnClient=MOBILE";
        }
        return "";
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void requireCustomer(String role) {
        if (!ROLE_CUSTOMER.equals(toPrepaidRole(role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String toPrepaidRole(String role) {
        if ("ROLE_CUSTOMER".equals(role) || "CUSTOMER".equals(role)) {
            return ROLE_CUSTOMER;
        }

        return "";
    }

}
