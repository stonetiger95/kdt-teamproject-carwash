package kdt.washPrj.chatbot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChatbotRateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxRequests;
    private final int windowSeconds;

    @Autowired
    public ChatbotRateLimiter(
            @Value("${chatbot.rate-limit.max-requests:20}") int maxRequests,
            @Value("${chatbot.rate-limit.window-seconds:60}") int windowSeconds
    ) {
        this(Clock.systemUTC(), maxRequests, windowSeconds);
    }

    ChatbotRateLimiter(Clock clock, int maxRequests, int windowSeconds) {
        this.clock = clock;
        this.maxRequests = Math.max(1, maxRequests);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public boolean allow(String key) {
        long currentWindow = clock.instant().getEpochSecond() / windowSeconds;
        Bucket bucket = buckets.compute(
                key,
                (ignored, existing) -> existing == null || existing.window != currentWindow
                        ? new Bucket(currentWindow)
                        : existing
        );

        if (buckets.size() > 10_000) {
            buckets.entrySet().removeIf(entry -> entry.getValue().window < currentWindow);
        }
        return bucket.count.incrementAndGet() <= maxRequests;
    }

    private static final class Bucket {
        private final long window;
        private final AtomicInteger count = new AtomicInteger();

        private Bucket(long window) {
            this.window = window;
        }
    }
}
