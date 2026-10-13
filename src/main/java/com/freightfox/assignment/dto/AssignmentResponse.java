package com.freightfox.assignment.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssignmentResponse(
        String status,
        Long assignmentRunId,
        BigDecimal totalCost,
        List<Object> selectedTransporters,
        int selectedTransporterCount,
        int lanesCovered,
        String optimizationObjective,
        List<AssignmentItemResponse> assignments
) {
    public static AssignmentResponse success(
            Long runId,
            BigDecimal totalCost,
            List<Object> selectedTransporters,
            int lanesCovered,
            String objective,
            List<AssignmentItemResponse> assignments
    ) {
        return new AssignmentResponse(
                "success",
                runId,
                totalCost,
                selectedTransporters,
                selectedTransporters.size(),
                lanesCovered,
                objective,
                assignments
        );
    }
}
