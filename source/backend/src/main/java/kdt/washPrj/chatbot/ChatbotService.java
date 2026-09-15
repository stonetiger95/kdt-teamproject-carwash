package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotHistoryMessageDTO;
import kdt.washPrj.chatbot.dto.ChatbotRequestDTO;
import kdt.washPrj.chatbot.dto.ChatbotResponseDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ChatbotService {

    private static final String FALLBACK_ANSWER =
            "정확한 답변을 확인하지 못했습니다. 상단 메뉴의 '문의사항'에서 질문을 남겨주세요.";
    private static final String CUSTOMER_LOGIN_ANSWER =
            "이 정보는 고객 로그인 후 확인할 수 있습니다. 로그인한 뒤 다시 질문해주세요.";
    private static final String ANONYMOUS_RESERVATION_ANSWER =
            "예약은 회원가입 후 예약을 하시거나 비회원의 경우 화면의 비회원 예약을 클릭하여 이용하실 수 있습니다.";

    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)01[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"
    );
    private static final Pattern CARD_NUMBER = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)"
    );
    private static final Pattern VEHICLE_PLATE = Pattern.compile(
            "(?<!\\d)\\d{2,3}[가-힣]\\d{4}(?!\\d)"
    );

    private final ChatbotKnowledgeBase knowledgeBase;
    private final OpenAiChatbotClient openAiClient;
    private final ChatbotToolService toolService;
    private final ChatbotFaqService faqService;
    private final ChatbotMetrics metrics;

    public ChatbotService(
            ChatbotKnowledgeBase knowledgeBase,
            OpenAiChatbotClient openAiClient,
            ChatbotToolService toolService,
            ChatbotFaqService faqService,
            ChatbotMetrics metrics
    ) {
        this.knowledgeBase = knowledgeBase;
        this.openAiClient = openAiClient;
        this.toolService = toolService;
        this.faqService = faqService;
        this.metrics = metrics;
    }

    public ChatbotResponseDTO reply(ChatbotRequestDTO request) {
        return reply(request, ChatbotUserContext.anonymous());
    }

    public ChatbotResponseDTO reply(
            ChatbotRequestDTO request,
            ChatbotUserContext context
    ) {
        long startedAt = System.nanoTime();
        try {
            ChatbotResponseDTO response = resolve(request, context);
            metrics.recordRequest(
                    response.source(),
                    "success",
                    System.nanoTime() - startedAt
            );
            return response;
        } catch (RuntimeException exception) {
            metrics.recordRequest("error", "failure", System.nanoTime() - startedAt);
            throw exception;
        }
    }

    private ChatbotResponseDTO resolve(
            ChatbotRequestDTO request,
            ChatbotUserContext context
    ) {
        if (isAnonymous(context) && asksHowToReserve(request.message())) {
            return new ChatbotResponseDTO(ANONYMOUS_RESERVATION_ANSWER, "TEMPLATE");
        }

        Optional<ChatbotToolResult> publicAnswer = toolService.answerPublicQuestion(
                request.message(),
                request.latitude(),
                request.longitude()
        );
        if (publicAnswer.isPresent()) {
            ChatbotToolResult result = publicAnswer.get();
            metrics.recordToolCall(result.name(), "success");
            return new ChatbotResponseDTO(result.answer(), "WASH_SITE", result.washSite());
        }

        Optional<String> detectedTool = toolService.detectTool(request.message());
        if (detectedTool.isPresent()) {
            if (context == null || !context.isCustomer()) {
                return new ChatbotResponseDTO(CUSTOMER_LOGIN_ANSWER, "TOOL");
            }
            try {
                ChatbotToolResult result = toolService.execute(detectedTool.get(), context);
                metrics.recordToolCall(result.name(), "success");
                return new ChatbotResponseDTO(result.answer(), "TOOL");
            } catch (RuntimeException exception) {
                metrics.recordToolCall(detectedTool.get(), "failure");
                throw exception;
            }
        }

        Optional<String> template = knowledgeBase.answer(request.message());
        if (template.isPresent()) {
            return new ChatbotResponseDTO(template.get(), "TEMPLATE");
        }

        String sanitizedMessage = redact(request.message());
        List<ChatbotHistoryMessageDTO> sanitizedHistory = request.history().stream()
                .map(item -> new ChatbotHistoryMessageDTO(
                        item.role(),
                        redact(item.content())
                ))
                .toList();

        Optional<OpenAiChatbotResult> generated = openAiClient.respondWithTools(
                modelInstructions(context),
                sanitizedHistory,
                sanitizedMessage,
                context,
                toolService
        );
        if (generated.isPresent()) {
            OpenAiChatbotResult result = generated.get();
            String source = result.toolNames().isEmpty() ? "GPT" : "TOOL";
            return new ChatbotResponseDTO(result.answer(), source);
        }

        faqService.recordUnresolved(sanitizedMessage, "NO_TEMPLATE_OR_GPT_ANSWER");
        return new ChatbotResponseDTO(FALLBACK_ANSWER, "FALLBACK");
    }

    private boolean isAnonymous(ChatbotUserContext context) {
        return context == null || !context.authenticated();
    }

    private boolean asksHowToReserve(String message) {
        String normalized = message == null
                ? ""
                : message.toLowerCase().replaceAll("\\s+", " ").trim();
        if (!normalized.contains("예약")
                || containsAny(
                        normalized,
                        "취소", "변경", "바꾸", "예약 조회", "예약 확인", "내 예약"
                )) {
            return false;
        }
        return containsAny(
                normalized,
                "어떻게", "방법", "예약하려", "예약하고 싶", "예약할 수", "예약 가능"
        );
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String modelInstructions(ChatbotUserContext context) {
        if (context != null && context.isCustomer()) {
            return knowledgeBase.systemInstructions() + """

                    로그인 고객의 예약, 결제 내역, 세차 내역, 잔액 질문에는 제공된 읽기 전용 도구를 사용하세요.
                    도구 인수로 고객 ID나 로그인 ID를 만들거나 요청하지 마세요.
                    """;
        }
        return knowledgeBase.systemInstructions();
    }

    String redact(String value) {
        String redacted = value == null ? "" : value;
        redacted = PHONE.matcher(redacted).replaceAll("[휴대전화번호]");
        redacted = EMAIL.matcher(redacted).replaceAll("[이메일]");
        redacted = CARD_NUMBER.matcher(redacted).replaceAll("[결제정보]");
        return VEHICLE_PLATE.matcher(redacted).replaceAll("[차량번호]");
    }
}
