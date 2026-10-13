package com.freightfox.assignment.optimizer;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Result representing the assignment of a specific lane to a transporter with the chosen quote amount.
 */
public record LaneAssignmentResult(
        String laneId,
        String transporterId,
        BigDecimal quoteAmount
) {
    public LaneAssignmentResult {
        Objects.requireNonNull(laneId, "Lane ID must not be null");
        Objects.requireNonNull(transporterId, "Transporter ID must not be null");
        Objects.requireNonNull(quoteAmount, "Quote amount must not be null");
    }
}
