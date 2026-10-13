package com.freightfox.assignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentItemResponse(
        Object laneId,
        Object transporterId,
        BigDecimal quoteAmount,
        String origin,
        String destination,
        String transporterName
) {
    public static AssignmentItemResponse of(
            Object laneId,
            Object transporterId,
            BigDecimal quoteAmount,
            String origin,
            String destination,
            String transporterName
    ) {
        return new AssignmentItemResponse(laneId, transporterId, quoteAmount, origin, destination, transporterName);
    }
}
