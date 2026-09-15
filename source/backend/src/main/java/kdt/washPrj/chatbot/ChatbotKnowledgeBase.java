package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotFaqDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ChatbotKnowledgeBase {

    private final ChatbotFaqService faqService;

    public ChatbotKnowledgeBase() {
        this(null);
    }

    @Autowired
    public ChatbotKnowledgeBase(ChatbotFaqService faqService) {
        this.faqService = faqService;
    }

    private static final String ENTRY_POLICY =
            "예약이 있어도 예약 시간과 관계없이 입차하여 세차를 이용할 수 있습니다. "
                    + "다만, 이용 가능한 세차기가 없으면 잠시 기다려야 할 수 있습니다.";
    private static final String RESERVATION_CANCEL_ANSWER =
            "회원의 경우 홈페이지 상단의 메뉴에서 세차 예약 선택 -> 내 예약 -> 예약 취소를 통해 "
                    + "예약 취소를 하신 후 새롭게 예약을 잡을 수 있습니다. "
                    + "비회원의 경우 홈화면의 비회원 예약 조회에 들어가신 후 정보를 입력하시고 "
                    + "예약 취소를 눌러 예약취소를 하시고 새로 예약을 잡으실 수 있습니다.";

    private static final String KNOWLEDGE_CONTEXT = """
            [확정된 Auto Wash 안내]
            - 예약이 있어도 예약 시간과 관계없이 입차하여 세차를 이용할 수 있다.
            - 이용 가능한 세차기가 없으면 고객이 기다려야 할 수 있다.
            - 고객의 실제 예약 정보는 로그인 후 '세차 예약 > 예약 조회'에서 확인한다.
            - 회원의 예약 변경은 '세차 예약 > 내 예약 > 예약 취소'에서 기존 예약을 취소한 뒤 새로 예약한다.
            - 비회원의 예약 변경은 홈의 '비회원 예약 조회'에서 정보를 입력하고 기존 예약을 취소한 뒤 새로 예약한다.
            - 세차 내역은 로그인 후 마이페이지 또는 세차 내역 메뉴에서 확인한다.
            - 선불 잔액, 마일리지, 쿠폰은 로그인 후 고객 대시보드에서 확인한다.
            - 결제, 예약 취소, 환불, 회원탈퇴는 챗봇이 직접 처리하지 않는다.
            - 회원탈퇴 전 선불 잔액은 0원이어야 한다.
            - SNS 전용 회원은 알 수 없는 내부 비밀번호를 입력하도록 안내하면 안 된다.
            - 근처 세차장 목록과 가격 질문에는 '근처 세차장' 입력을 안내하고, 세차장 이름이 있으면 구체적인 가격을 안내한다.
            - 계정, 결제 또는 예약의 개인정보를 채팅에 입력하도록 요구하지 않는다.
            """;

    public Optional<String> answer(String message) {
        String normalized = normalize(message);

        if (normalized.contains("예약")
                && containsAny(normalized, "변경", "취소", "바꾸")) {
            return Optional.of(RESERVATION_CANCEL_ANSWER);
        }

        Optional<String> managedAnswer = managedFaqAnswer(normalized);
        if (managedAnswer.isPresent()) {
            return managedAnswer;
        }

        if (containsAny(normalized, "입차", "30분 전", "예약시간이 아닙니다", "예약 시간이 다릅니다")) {
            return Optional.of(ENTRY_POLICY);
        }
        if (containsAny(normalized, "내 예약", "예약 조회", "예약 확인", "예약 몇 시")) {
            return Optional.of(
                    "로그인 후 '세차 예약 > 예약 조회'에서 현재 예약 시간과 상태를 확인할 수 있습니다. "
                            + "개인 예약정보는 채팅창에 입력하지 마세요."
            );
        }
        if (containsAny(normalized, "세차 내역", "이용 내역")) {
            return Optional.of("로그인 후 마이페이지의 세차 내역에서 확인할 수 있습니다.");
        }
        if (containsAny(normalized, "선불", "잔액", "마일리지", "쿠폰")) {
            return Optional.of(
                    "로그인 후 고객 대시보드에서 선불 잔액, 마일리지와 쿠폰을 확인할 수 있습니다."
            );
        }
        if (containsAny(normalized, "회원탈퇴", "계정 삭제", "sns 회원 탈퇴", "소셜 회원 탈퇴")) {
            return Optional.of(
                    "회원탈퇴는 마이페이지에서 진행합니다. 선불 잔액이 0원이어야 하며, "
                            + "SNS 전용 회원에게 비밀번호 입력이 요구되면 고객 문의로 본인확인을 요청해주세요."
            );
        }
        if (containsAny(normalized, "로그아웃", "로그 아웃")) {
            return Optional.of("화면 오른쪽 위의 Logout 버튼을 누르면 비밀번호 없이 로그아웃됩니다.");
        }
        if (containsAny(normalized, "운영시간", "영업시간", "몇 시까지", "가격", "요금")) {
            return Optional.of(
                    "근처에 있는 세차장 목록과 가격을 원하시면 \"근처 세차장\"을 입력해주시고 "
                            + "세차장 이름을 입력하시면 구체적인 가격을 알려드리겠습니다."
            );
        }
        if (containsAny(normalized, "환불", "결제 취소")) {
            return Optional.of(
                    "환불 가능 여부는 결제 및 이용 상태에 따라 달라집니다. "
                            + "결제 내역을 확인한 뒤 문의사항 메뉴로 요청해주세요."
            );
        }
        if (containsAny(normalized, "결제 방법", "결제하고 싶", "결제하려", "어디서 결제", "결제 어디서")) {
            return Optional.of(
                    "챗봇은 결제를 직접 처리하지 않습니다. 로그인 회원은 세차 내역 또는 결제 화면에서 "
                            + "결제할 항목을 확인해주세요. 비회원은 홈의 '비회원 세차 조회·결제'를 이용해주세요."
            );
        }

        return Optional.empty();
    }

    public String systemInstructions() {
        String managedContext = managedFaqs().stream()
                .limit(30)
                .map(faq -> "- 질문: %s\n  답변: %s".formatted(
                        faq.getQuestion(), faq.getAnswer()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- 등록된 관리자 FAQ가 없습니다.");
        return """
                당신은 Auto Wash 고객 안내 챗봇입니다.
                한국어로 짧고 친절하게 답하세요.
                아래 확정 정보만 사실로 사용하고, 모르는 내용은 추측하지 마세요.
                고객의 비밀번호, 전체 전화번호, 전체 차량번호, 카드정보를 요구하지 마세요.
                예약, 결제, 환불, 회원탈퇴를 완료했다고 말하지 마세요.
                개인 계정 정보가 필요한 질문에는 해당 화면에서 확인하도록 안내하세요.
                확정 정보로 답할 수 없으면 '문의사항' 메뉴를 이용하도록 안내하세요.

                """ + KNOWLEDGE_CONTEXT + "\n[관리자 FAQ]\n" + managedContext;
    }

    private Optional<String> managedFaqAnswer(String normalizedMessage) {
        if (normalizedMessage.isBlank()) {
            return Optional.empty();
        }
        for (ChatbotFaqDTO faq : managedFaqs()) {
            if (faq == null || faq.getAnswer() == null || faq.getAnswer().isBlank()) {
                continue;
            }
            String question = normalize(faq.getQuestion());
            if (!question.isBlank()
                    && (normalizedMessage.equals(question)
                    || (question.length() >= 5 && normalizedMessage.contains(question)))) {
                return Optional.of(faq.getAnswer().trim());
            }
            if (faq.getKeywords() == null) {
                continue;
            }
            for (String keyword : faq.getKeywords().split(",")) {
                String normalizedKeyword = normalize(keyword);
                if (normalizedKeyword.length() >= 2 && normalizedMessage.contains(normalizedKeyword)) {
                    return Optional.of(faq.getAnswer().trim());
                }
            }
        }
        return Optional.empty();
    }

    private List<ChatbotFaqDTO> managedFaqs() {
        return faqService == null ? List.of() : faqService.enabledFaqs();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
