package kdt.washPrj.chatbot;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kdt.washPrj.chatbot.dto.ChatbotRequestDTO;
import kdt.washPrj.chatbot.dto.ChatbotResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/public/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final ChatbotFaqService faqService;
    private final ChatbotRateLimiter rateLimiter;
    private final ChatbotMetrics metrics;

    public ChatbotController(
            ChatbotService chatbotService,
            ChatbotFaqService faqService,
            ChatbotRateLimiter rateLimiter,
            ChatbotMetrics metrics
    ) {
        this.chatbotService = chatbotService;
        this.faqService = faqService;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @GetMapping("/faqs")
    public List<String> faqQuestions() {
        return faqService.enabledFaqs().stream()
                .map(faq -> faq.getQuestion() == null ? "" : faq.getQuestion().trim())
                .filter(question -> !question.isBlank())
                .limit(6)
                .toList();
    }

    @PostMapping("/messages")
    public ChatbotResponseDTO reply(
            @Valid @RequestBody ChatbotRequestDTO request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        boolean signedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        String rateLimitKey = signedIn
                ? "user:" + authentication.getName()
                : "ip:" + clientAddress(servletRequest);
        if (!rateLimiter.allow(rateLimitKey)) {
            metrics.recordRateLimitRejection();
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "잠시 후 다시 질문해주세요."
            );
        }
        return chatbotService.reply(request, ChatbotUserContext.from(authentication));
    }

    private String clientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if ("127.0.0.1".equals(remoteAddress) || "::1".equals(remoteAddress)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return remoteAddress == null ? "unknown" : remoteAddress;
    }
}
