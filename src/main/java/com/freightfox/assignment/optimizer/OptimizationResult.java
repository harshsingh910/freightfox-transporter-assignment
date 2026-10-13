package com.freightfox.assignment.optimizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Outcome of executing the optimization algorithm.
 */
public record OptimizationResult(
        boolean feasible,
        String infeasibilityReason,
        BigDecimal totalCost,
        Set<String> selectedTransporterIds,
        int selectedTransporterCount,
        int lanesCovered,
        List<LaneAssignmentResult> assignments,
        OptimizationObjective objective,
        long executionTimeMs
) {
    public static OptimizationResult infeasible(
            String reason,
            OptimizationObjective objective,
            long executionTimeMs
    ) {
        return new OptimizationResult(
                false,
                reason,
                BigDecimal.ZERO,
                Set.of(),
                0,
                0,
                List.of(),
                objective,
                executionTimeMs
        );
    }

    public static OptimizationResult feasible(
            BigDecimal totalCost,
            Set<String> selectedTransporterIds,
            int lanesCovered,
            List<LaneAssignmentResult> assignments,
            OptimizationObjective objective,
            long executionTimeMs
    ) {
        return new OptimizationResult(
                true,
                null,
                totalCost,
                Set.copyOf(selectedTransporterIds),
                selectedTransporterIds.size(),
                lanesCovered,
                List.copyOf(assignments),
                objective,
                executionTimeMs
        );
    }
}
