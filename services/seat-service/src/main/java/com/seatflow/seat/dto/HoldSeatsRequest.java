package com.seatflow.seat.dto;

import java.util.List;

public record HoldSeatsRequest(List<Long> seatIds) {}