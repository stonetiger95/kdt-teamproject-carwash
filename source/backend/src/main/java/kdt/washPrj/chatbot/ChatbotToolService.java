package kdt.washPrj.chatbot;

import kdt.washPrj.benefit.BenefitService;
import kdt.washPrj.benefit.dto.CustomerBenefitSummaryDTO;
import kdt.washPrj.chatbot.dto.ChatbotCustomerBalanceDTO;
import kdt.washPrj.chatbot.dto.ChatbotWashSiteMapDTO;
import kdt.washPrj.global.pagenation.PageResponseDTO;
import kdt.washPrj.payment.PaymentQueryService;
import kdt.washPrj.payment.dto.PaymentResponseDTO;
import kdt.washPrj.payment.dto.PaymentSearchCondition;
import kdt.washPrj.reservation.ReservationQueryService;
import kdt.washPrj.reservation.dto.ReservationResponseDTO;
import kdt.washPrj.reservation.dto.ReservationSearchDTO;
import kdt.washPrj.wash.WashQueryService;
import kdt.washPrj.wash.dto.WashHistorySearchDTO;
import kdt.washPrj.wash.dto.WashResponseDTO;
import kdt.washPrj.washsite.WashSiteService;
import kdt.washPrj.washsite.dto.WashSiteMapDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatbotToolService {

    public static final String GET_MY_RESERVATIONS = "get_my_reservations";
    public static final String GET_MY_PAYMENTS = "get_my_payments";
    public static final String GET_MY_WASH_HISTORY = "get_my_wash_history";
    public static final String GET_MY_BALANCES = "get_my_balances";
    public static final String GET_WASH_SITE_PRICE = "get_wash_site_price";
    public static final String GET_WASH_SITE_LOCATION = "get_wash_site_location";
    public static final String GET_NEARBY_WASH_SITES = "get_nearby_wash_sites";
    private static final String WASH_SITE_PRICE_GUIDE =
            "근처에 있는 세차장 목록과 가격을 원하시면 \"근처 세차장\"을 입력해주시고 "
                    + "세차장 이름을 입력하시면 구체적인 가격을 알려드리겠습니다.";
    private static final double NEARBY_RADIUS_KM = 20.0;
    private static final int NEARBY_RESULT_LIMIT = 5;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> GENERIC_WASH_SITE_WORDS = Set.of(
            "세차장", "세차", "무정차"
    );

    private final ReservationQueryService reservationQueryService;
    private final PaymentQueryService paymentQueryService;
    private final WashQueryService washQueryService;
    private final ChatbotCustomerDataMapper customerDataMapper;
    private final BenefitService benefitService;
    private final WashSiteService washSiteService;

    public ChatbotToolService(
            ReservationQueryService reservationQueryService,
            PaymentQueryService paymentQueryService,
            WashQueryService washQueryService,
            ChatbotCustomerDataMapper customerDataMapper,
            BenefitService benefitService,
            WashSiteService washSiteService
    ) {
        this.reservationQueryService = reservationQueryService;
        this.paymentQueryService = paymentQueryService;
        this.washQueryService = washQueryService;
        this.customerDataMapper = customerDataMapper;
        this.benefitService = benefitService;
        this.washSiteService = washSiteService;
    }

    public Optional<ChatbotToolResult> answerPublicQuestion(String message) {
        return answerPublicQuestion(message, null, null);
    }

    public Optional<ChatbotToolResult> answerPublicQuestion(
            String message,
            Double latitude,
            Double longitude
    ) {
        String normalized = normalize(message);
        if (containsAny(normalized, "근처 세차장", "가까운 세차장", "주변 세차장")) {
            return Optional.of(nearbyWashSites(latitude, longitude));
        }

        boolean priceQuestion = containsAny(normalized, "가격", "요금", "얼마");
        boolean operatingHoursQuestion = containsAny(
                normalized, "운영시간", "영업시간", "몇 시까지", "몇시까지"
        );
        boolean locationQuestion = containsAny(normalized, "위치", "주소", "지도")
                || !priceQuestion && !operatingHoursQuestion && normalized.contains("어디");
        if (!priceQuestion && !operatingHoursQuestion && !locationQuestion) {
            return Optional.empty();
        }

        List<WashSiteMapDTO> matchingSites = washSiteService.getMapWashSites(
                        null, null, null, null, null, null, null, null
                ).stream()
                .filter(site -> site != null && matchesSpecificSite(normalized, site.getSiteName()))
                .toList();

        if (matchingSites.size() != 1) {
            if (priceQuestion || operatingHoursQuestion) {
                return Optional.of(result(GET_WASH_SITE_PRICE, WASH_SITE_PRICE_GUIDE));
            }
            return Optional.empty();
        }

        WashSiteMapDTO site = matchingSites.get(0);
        String siteName = safe(site.getSiteName());
        if (locationQuestion) {
            String address = safe(site.getAddress());
            String answer = "정보 없음".equals(address)
                    ? "%s의 주소는 현재 등록되어 있지 않습니다.".formatted(siteName)
                    : "%s의 위치는 %s입니다.".formatted(siteName, address);
            return Optional.of(new ChatbotToolResult(
                    GET_WASH_SITE_LOCATION,
                    answer,
                    answer,
                    new ChatbotWashSiteMapDTO(
                            site.getWashSiteId(),
                            site.getSiteName(),
                            site.getAddress(),
                            site.getLatitude(),
                            site.getLongitude()
                    )
            ));
        }

        if (site.getWashPrice() == null) {
            return Optional.of(result(
                    GET_WASH_SITE_PRICE,
                    "%s의 기본 세차 가격은 현재 등록되어 있지 않습니다. 세차장 상세 화면에서 확인해주세요."
                            .formatted(siteName)
            ));
        }

        String courseName = safe(site.getCourseName());
        return Optional.of(result(
                GET_WASH_SITE_PRICE,
                "%s의 %s 가격은 %,d원입니다."
                        .formatted(siteName, courseName, site.getWashPrice())
        ));
    }

    private ChatbotToolResult nearbyWashSites(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return result(
                    GET_NEARBY_WASH_SITES,
                    "근처 세차장을 찾으려면 브라우저의 위치 권한을 허용해주세요."
            );
        }

        List<WashSiteMapDTO> nearbySites = washSiteService.getMapWashSites(
                        null,
                        latitude,
                        longitude,
                        NEARBY_RADIUS_KM,
                        null,
                        null,
                        null,
                        null
                ).stream()
                .filter(site -> site != null && site.getDistanceKm() != null)
                .limit(NEARBY_RESULT_LIMIT)
                .toList();

        if (nearbySites.isEmpty()) {
            return result(
                    GET_NEARBY_WASH_SITES,
                    "현재 위치 반경 20km 안에 등록된 세차장이 없습니다."
            );
        }

        List<String> lines = nearbySites.stream()
                .map(site -> "- %s · %s · %s".formatted(
                        safe(site.getSiteName()),
                        site.getWashPrice() == null
                                ? "가격 정보 없음"
                                : "%,d원".formatted(site.getWashPrice()),
                        String.format(Locale.ROOT, "%.2fkm", site.getDistanceKm())
                ))
                .toList();
        return result(
                GET_NEARBY_WASH_SITES,
                "현재 위치에서 가까운 세차장입니다.\n%s".formatted(String.join("\n", lines))
        );
    }

    public List<String> availableToolNames(ChatbotUserContext context) {
        return context != null && context.isCustomer()
                ? List.of(
                        GET_MY_RESERVATIONS,
                        GET_MY_PAYMENTS,
                        GET_MY_WASH_HISTORY,
                        GET_MY_BALANCES
                )
                : List.of();
    }

    public Optional<String> detectTool(String message) {
        String normalized = message == null
                ? ""
                : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (isLookupIntent(normalized, "예약", "확인", "조회", "알려", "상태", "언제", "몇 시", "몇시", "잡혀")) {
            return Optional.of(GET_MY_RESERVATIONS);
        }
        if (isLookupIntent(normalized, "결제", "내역", "확인", "조회", "알려", "상태", "언제", "얼마", "완료", "됐")) {
            return Optional.of(GET_MY_PAYMENTS);
        }
        if (containsAny(normalized, "내 세차 내역", "세차 내역", "이용 내역", "최근 세차")) {
            return Optional.of(GET_MY_WASH_HISTORY);
        }
        boolean personalBenefitQuestion = normalized.contains("내 ")
                && containsAny(normalized, "잔액", "마일리지", "쿠폰", "혜택");
        if (personalBenefitQuestion || containsAny(
                normalized,
                "내 잔액", "선불 잔액", "마일리지 잔액", "혜택 잔액",
                "사용 가능한 쿠폰", "쿠폰 몇"
        )) {
            return Optional.of(GET_MY_BALANCES);
        }
        return Optional.empty();
    }

    public ChatbotToolResult execute(String toolName, ChatbotUserContext context) {
        requireCustomer(context);
        return switch (toolName) {
            case GET_MY_RESERVATIONS -> reservations(context.loginId());
            case GET_MY_PAYMENTS -> payments(context.loginId());
            case GET_MY_WASH_HISTORY -> washHistory(context.loginId());
            case GET_MY_BALANCES -> balances(context.loginId());
            default -> throw new IllegalArgumentException("허용되지 않은 챗봇 도구입니다.");
        };
    }

    private ChatbotToolResult reservations(String loginId) {
        ReservationSearchDTO search = new ReservationSearchDTO();
        search.setPage(1);
        search.setSize(5);
        PageResponseDTO<ReservationResponseDTO> page =
                reservationQueryService.getCustomerReservationPage(loginId, search);
        List<ReservationResponseDTO> reservations = page.getContent();
        if (reservations == null || reservations.isEmpty()) {
            return result(GET_MY_RESERVATIONS, "현재 확인되는 예약이 없습니다.");
        }

        List<String> lines = new ArrayList<>();
        for (ReservationResponseDTO reservation : reservations) {
            lines.add("- %s · %s · %s".formatted(
                    safe(reservation.getSiteName()),
                    format(reservation.getStartTime()),
                    reservation.getStatus() == null ? "상태 미확인" : reservation.getStatus().name()
            ));
        }
        return result(GET_MY_RESERVATIONS,
                "최근 예약 %d건입니다.\n%s".formatted(lines.size(), String.join("\n", lines)));
    }

    private ChatbotToolResult washHistory(String loginId) {
        WashHistorySearchDTO search = new WashHistorySearchDTO();
        search.setPage(1);
        search.setSize(5);
        PageResponseDTO<WashResponseDTO> page =
                washQueryService.getWashHistoryPage(loginId, search);
        List<WashResponseDTO> history = page.getContent();
        if (history == null || history.isEmpty()) {
            return result(GET_MY_WASH_HISTORY, "현재 확인되는 세차 내역이 없습니다.");
        }

        List<String> lines = new ArrayList<>();
        for (WashResponseDTO wash : history) {
            LocalDateTime usedAt = wash.getWashEndTime() != null
                    ? wash.getWashEndTime()
                    : wash.getEntryTime();
            lines.add("- %s · %s · %s · %s".formatted(
                    safe(wash.getSiteName()),
                    format(usedAt),
                    safe(wash.getCourseName()),
                    safe(wash.getWashStatus())
            ));
        }
        return result(GET_MY_WASH_HISTORY,
                "최근 세차 내역 %d건입니다.\n%s".formatted(lines.size(), String.join("\n", lines)));
    }

    private ChatbotToolResult payments(String loginId) {
        List<PaymentResponseDTO> payments = paymentQueryService.getPaymentList(
                loginId,
                "ROLE_CUSTOMER",
                new PaymentSearchCondition()
        );
        if (payments == null || payments.isEmpty()) {
            return result(GET_MY_PAYMENTS, "현재 확인되는 결제 내역이 없습니다.");
        }

        List<String> lines = payments.stream()
                .limit(5)
                .map(payment -> "- %s · %s · %,d원 · %s".formatted(
                        format(paymentTime(payment)),
                        paymentPurpose(payment.getPurpose()),
                        payment.getAmount() == null ? 0 : payment.getAmount(),
                        paymentStatus(payment.getStatus())
                ))
                .toList();
        return result(GET_MY_PAYMENTS,
                "최근 결제 내역 %d건입니다.\n%s".formatted(lines.size(), String.join("\n", lines)));
    }

    private ChatbotToolResult balances(String loginId) {
        ChatbotCustomerBalanceDTO prepaid = customerDataMapper.selectCustomerBalance(loginId);
        CustomerBenefitSummaryDTO benefits = benefitService.getMySummary(loginId);
        int prepaidBalance = prepaid == null || prepaid.getPrepaidBalance() == null
                ? 0 : prepaid.getPrepaidBalance();
        int mileage = benefits == null || benefits.getTotalMileageBalance() == null
                ? 0 : benefits.getTotalMileageBalance();
        long coupons = benefits == null || benefits.getTotalAvailableCouponCount() == null
                ? 0L : benefits.getTotalAvailableCouponCount();
        return result(GET_MY_BALANCES,
                "현재 선불 잔액은 %,d원, 마일리지는 %,d점, 사용 가능한 쿠폰은 %,d장입니다."
                        .formatted(prepaidBalance, mileage, coupons));
    }

    private ChatbotToolResult result(String name, String answer) {
        return new ChatbotToolResult(name, answer, answer);
    }

    private void requireCustomer(ChatbotUserContext context) {
        if (context == null || !context.isCustomer()) {
            throw new IllegalStateException("고객 로그인이 필요합니다.");
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSpecificSite(String normalizedMessage, String siteName) {
        List<String> identifyingWords = List.of(normalize(siteName).split(" ")).stream()
                .filter(word -> !word.isBlank())
                .filter(word -> !GENERIC_WASH_SITE_WORDS.contains(word))
                .toList();
        return !identifyingWords.isEmpty()
                && identifyingWords.stream().allMatch(normalizedMessage::contains);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^0-9a-z가-힣]+", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private boolean isLookupIntent(
            String value,
            String topic,
            String... intentWords
    ) {
        return value.contains(topic) && containsAny(value, intentWords);
    }

    private LocalDateTime paymentTime(PaymentResponseDTO payment) {
        if (payment.getApprovedAt() != null) {
            return payment.getApprovedAt();
        }
        if (payment.getRequestedAt() != null) {
            return payment.getRequestedAt();
        }
        return payment.getCreatedAt();
    }

    private String paymentPurpose(String purpose) {
        if (purpose == null) {
            return "결제";
        }
        return switch (purpose) {
            case "CHARGE" -> "선불 충전";
            case "WASH_PAYMENT" -> "세차 결제";
            case "RESERVATION_PAYMENT" -> "예약 결제";
            case "REFUND" -> "환불";
            default -> "결제";
        };
    }

    private String paymentStatus(String status) {
        if (status == null) {
            return "상태 미확인";
        }
        return switch (status) {
            case "WAITING" -> "결제 대기";
            case "SUCCESS" -> "결제 완료";
            case "CANCELED" -> "결제 취소";
            case "FAILED" -> "결제 실패";
            case "PARTIAL_REFUNDED" -> "부분 환불";
            case "REFUNDED" -> "환불 완료";
            default -> status;
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "정보 없음" : value.trim();
    }

    private String format(LocalDateTime value) {
        return value == null ? "시간 미확인" : value.format(DATE_TIME);
    }
}
