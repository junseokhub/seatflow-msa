package com.seatflow.common.event;

public final class EventTopic {

    private EventTopic() {}

    public static final String USER_REGISTERED = "user.registered";

    public static final String SEAT_HELD = "seat.held";

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String SHOW_CREATED = "show.created";
}