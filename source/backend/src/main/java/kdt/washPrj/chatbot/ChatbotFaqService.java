package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotFaqDTO;
import kdt.washPrj.chatbot.dto.ChatbotFaqRequestDTO;
import kdt.washPrj.chatbot.dto.ChatbotUnresolvedQuestionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Service
public class ChatbotFaqService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotFaqService.class);
    private static final long CACHE_NANOS = TimeUnit.SECONDS.toNanos(30);
    private final ChatbotFaqMapper mapper;
    private volatile List<ChatbotFaqDTO> cachedEnabledFaqs = List.of();
    private volatile long cacheExpiresAt;

    public ChatbotFaqService(ChatbotFaqMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized List<ChatbotFaqDTO> enabledFaqs() {
        long now = System.nanoTime();
        if (now < cacheExpiresAt) {
            return cachedEnabledFaqs;
        }
        try {
            List<ChatbotFaqDTO> loaded = mapper.selectEnabledFaqs();
            cachedEnabledFaqs = loaded == null ? List.of() : List.copyOf(loaded);
        } catch (DataAccessException exception) {
            log.warn("Chatbot FAQ database is unavailable; built-in templates remain active");
        }
        cacheExpiresAt = now + CACHE_NANOS;
        return cachedEnabledFaqs;
    }

    public void recordUnresolved(String maskedQuestion, String reason) {
        try {
            mapper.recordUnresolved(trim(maskedQuestion, 1000), trim(reason, 80));
        } catch (DataAccessException exception) {
            log.warn("Could not store unresolved chatbot question");
        }
    }

    public List<ChatbotFaqDTO> adminFaqs(String loginId) {
        try {
            return mapper.selectAdminFaqs(loginId);
        } catch (DataAccessException exception) {
            throw adminFailure(exception);
        }
    }

    public Long create(String loginId, ChatbotFaqRequestDTO request) {
        try {
            Long id = mapper.saveAdminFaq(loginId, null, request);
            invalidateCache();
            return id;
        } catch (DataAccessException exception) {
            throw adminFailure(exception);
        }
    }

    public Long update(String loginId, Long faqId, ChatbotFaqRequestDTO request) {
        try {
            Long id = mapper.saveAdminFaq(loginId, faqId, request);
            invalidateCache();
            return id;
        } catch (DataAccessException exception) {
            throw adminFailure(exception);
        }
    }

    public void delete(String loginId, Long faqId) {
        try {
            if (!Boolean.TRUE.equals(mapper.deleteAdminFaq(loginId, faqId))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FAQ를 찾을 수 없습니다.");
            }
            invalidateCache();
        } catch (DataAccessException exception) {
            throw adminFailure(exception);
        }
    }

    public List<ChatbotUnresolvedQuestionDTO> unresolved(String loginId) {
        try {
            return mapper.selectUnresolved(loginId);
        } catch (DataAccessException exception) {
            throw adminFailure(exception);
        }
    }

    public void resolve(String loginId, Long questionId) {
        try {
            if (!Boolean.TRUE.equals(mapper.resolveUnresolved(loginId, questionId))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "미해결 질문을 찾을 수 없습니다.");
            }
        } catch (DataAccessException exception) {
            throw adminFailure(exception);
        }
    }

    private RuntimeException adminFailure(DataAccessException exception) {
        SQLException sqlException = sqlException(exception);
        String sqlState = sqlException == null ? "" : sqlException.getSQLState();
        if (sqlState != null && sqlState.startsWith("42")) {
            log.warn("Chatbot administration database functions are unavailable");
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "챗봇 관리 DB 마이그레이션(58)을 먼저 적용해주세요.",
                    exception
            );
        }
        return exception;
    }

    private void invalidateCache() {
        cacheExpiresAt = 0;
    }

    private SQLException sqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sql) {
                return sql;
            }
            current = current.getCause();
        }
        return null;
    }

    private String trim(String value, int maxLength) {
        String resolved = value == null ? "" : value.trim();
        return resolved.length() <= maxLength ? resolved : resolved.substring(0, maxLength);
    }
}
