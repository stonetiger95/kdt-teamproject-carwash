package kdt.washPrj.reservation;

import java.time.ZoneId;

/**
 * 예약 생성, 취소, 입차 및 노쇼 판정에 공통으로 사용하는 정책입니다.
 */
public final class ReservationPolicy {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    public static final int DURATION_MINUTES = 30;
    public static final int MIN_ADVANCE_HOURS = 1;
    public static final int MAX_ADVANCE_DAYS = 7;
    public static final int CANCEL_CUTOFF_HOURS = 1;
    public static final int ARRIVAL_GRACE_MINUTES = 15;
    public static final int PAYMENT_TIMEOUT_MINUTES = 15;

    private ReservationPolicy() {
    }
}
