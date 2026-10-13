package com.freightfox.assignment.optimizer;

import java.util.List;
import java.util.Objects;

/**
 * Immutable formulation of the transporter assignment optimization problem.
 */
public record OptimizationProblem(
        List<OptimizationLane> lanes,
        List<OptimizationTransporter> transporters,
        List<OptimizationQuote> quotes,
        int maxTransporters,
        OptimizationObjective objective,
        int maxCombinatorialSubsets,
        long maxRuntimeMillis
) {
    public static final int DEFAULT_MAX_COMBINATORIAL_SUBSETS = 100_000;
    public static final long DEFAULT_MAX_RUNTIME_MILLIS = 30_000L;

    public OptimizationProblem {
        Objects.requireNonNull(lanes, "Lanes list must not be null");
        Objects.requireNonNull(transporters, "Transporters list must not be null");
        Objects.requireNonNull(quotes, "Quotes list must not be null");
        Objects.requireNonNull(objective, "Optimization objective must not be null");
        if (maxTransporters < 1) {
            throw new IllegalArgumentException("maxTransporters must be >= 1. Received: " + maxTransporters);
        }
        if (maxCombinatorialSubsets < 1) {
            maxCombinatorialSubsets = DEFAULT_MAX_COMBINATORIAL_SUBSETS;
        }
        if (maxRuntimeMillis < 1) {
            maxRuntimeMillis = DEFAULT_MAX_RUNTIME_MILLIS;
        }
        lanes = List.copyOf(lanes);
        transporters = List.copyOf(transporters);
        quotes = List.copyOf(quotes);
    }

    public static OptimizationProblem of(
            List<OptimizationLane> lanes,
            List<OptimizationTransporter> transporters,
            List<OptimizationQuote> quotes,
            int maxTransporters
    ) {
        return new OptimizationProblem(
                lanes,
                transporters,
                quotes,
                maxTransporters,
                OptimizationObjective.LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST,
                DEFAULT_MAX_COMBINATORIAL_SUBSETS,
                DEFAULT_MAX_RUNTIME_MILLIS
        );
    }
}
