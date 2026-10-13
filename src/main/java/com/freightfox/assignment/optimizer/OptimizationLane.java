package com.freightfox.assignment.optimizer;

import java.util.Objects;

/**
 * Domain record representing a trade lane in the optimization engine.
 */
public record OptimizationLane(
        String id,
        String origin,
        String destination
) {
    public OptimizationLane {
        Objects.requireNonNull(id, "Lane ID must not be null");
        Objects.requireNonNull(origin, "Lane origin must not be null");
        Objects.requireNonNull(destination, "Lane destination must not be null");
    }
}
