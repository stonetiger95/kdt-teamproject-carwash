package kdt.washPrj.reservation;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class ReservationTimeProvider {

    private final Clock clock;

    public ReservationTimeProvider() {
        this(Clock.system(ReservationPolicy.ZONE_ID));
    }

    public ReservationTimeProvider(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
