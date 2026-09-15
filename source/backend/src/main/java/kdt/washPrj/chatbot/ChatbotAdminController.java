package kdt.washPrj.chatbot;

import jakarta.validation.Valid;
import kdt.washPrj.chatbot.dto.ChatbotFaqDTO;
import kdt.washPrj.chatbot.dto.ChatbotFaqRequestDTO;
import kdt.washPrj.chatbot.dto.ChatbotMetricSummaryDTO;
import kdt.washPrj.chatbot.dto.ChatbotUnresolvedQuestionDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/chatbot")
public class ChatbotAdminController {

    private final ChatbotFaqService faqService;
    private final ChatbotMetrics metrics;

    public ChatbotAdminController(ChatbotFaqService faqService, ChatbotMetrics metrics) {
        this.faqService = faqService;
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public ChatbotMetricSummaryDTO metrics() {
        return metrics.snapshot();
    }

    @GetMapping("/faqs")
    public List<ChatbotFaqDTO> faqs(Authentication authentication) {
        return faqService.adminFaqs(authentication.getName());
    }

    @PostMapping("/faqs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(
            Authentication authentication,
            @Valid @RequestBody ChatbotFaqRequestDTO request
    ) {
        return Map.of("chatbotFaqId", faqService.create(authentication.getName(), request));
    }

    @PutMapping("/faqs/{faqId}")
    public Map<String, Long> update(
            Authentication authentication,
            @PathVariable Long faqId,
            @Valid @RequestBody ChatbotFaqRequestDTO request
    ) {
        return Map.of("chatbotFaqId", faqService.update(authentication.getName(), faqId, request));
    }

    @DeleteMapping("/faqs/{faqId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long faqId) {
        faqService.delete(authentication.getName(), faqId);
    }

    @GetMapping("/unresolved-questions")
    public List<ChatbotUnresolvedQuestionDTO> unresolved(Authentication authentication) {
        return faqService.unresolved(authentication.getName());
    }

    @PatchMapping("/unresolved-questions/{questionId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(Authentication authentication, @PathVariable Long questionId) {
        faqService.resolve(authentication.getName(), questionId);
    }
}
