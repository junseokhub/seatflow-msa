//package com.seatflow.user.domain;
//
//import jakarta.persistence.Column;
//import jakarta.persistence.Embeddable;
//import lombok.AccessLevel;
//import lombok.EqualsAndHashCode;
//import lombok.Getter;
//import lombok.NoArgsConstructor;
//
//import java.io.Serializable;
//
//@Embeddable
//@Getter
//@EqualsAndHashCode
//@NoArgsConstructor(access = AccessLevel.PROTECTED)
//public class ProcessedEventId implements Serializable {
//
//    @Column(name = "consumer_group", nullable = false, updatable = false)
//    private String consumerGroup;
//
//    @Column(name = "event_id", nullable = false, updatable = false)
//    private String eventId;
//
//    public ProcessedEventId(String consumerGroup, String eventId) {
//        this.consumerGroup = consumerGroup;
//        this.eventId = eventId;
//    }
//}