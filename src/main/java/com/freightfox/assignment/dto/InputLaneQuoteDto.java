package com.freightfox.assignment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InputLaneQuoteDto(
        @NotNull(message = "Lane ID in quote is required")
        Object laneId,

        @NotNull(message = "Quote amount is required")
        @DecimalMin(value = "0.01", message = "Quote amount must be positive")
        BigDecimal quote
) {
    public String getExternalLaneId() {
        return String.valueOf(laneId);
    }
}
