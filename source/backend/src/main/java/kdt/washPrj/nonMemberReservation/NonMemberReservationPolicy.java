package kdt.washPrj.nonMemberReservation;

import kdt.washPrj.reservation.ReservationPolicy;

public final class NonMemberReservationPolicy {

    public static final int DURATION_MINUTES = ReservationPolicy.DURATION_MINUTES;
    public static final int MIN_ADVANCE_HOURS = ReservationPolicy.MIN_ADVANCE_HOURS;
    public static final int MAX_ADVANCE_DAYS = ReservationPolicy.MAX_ADVANCE_DAYS;
    public static final int PAYMENT_TIMEOUT_MINUTES = ReservationPolicy.PAYMENT_TIMEOUT_MINUTES;

    private NonMemberReservationPolicy() {
    }
}
