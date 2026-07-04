package com.seatflow.common.event;

public final class EventTopic {

    private EventTopic() {}

    public static final String USER_REGISTERED = "user.registered";

    public static final String SEAT_HELD = "seat.held";

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String SHOW_CREATED = "show.created";

    public static final String RESERVATION_CONFIRMED = "reservation.confirmed";

    public static final String SEAT_RELEASE_COMMAND = "seat.release.command";
    public static final String SEAT_RELEASED = "seat.released";
    public static final String SEAT_RESERVE_COMPENSATION_COMMAND = "seat.reserve.compensation.command";
    public static final String SEAT_RESERVED_COMPENSATED = "seat.reserved.compensated";

    public static final String PAYMENT_REFUND_COMMAND = "payment.refund.command";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String PAYMENT_REFUND_FAILED = "payment.refund.failed";
}