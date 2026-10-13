package com.freightfox.assignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO representing an assignment run in paginated history responses.
 * Completely decouples web serialization from Hibernate entities to prevent LazyInitializationException.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentRunResponse(
        Long id,
        Long assignmentRunId,
        int requestedMaxTransporters,
        int selectedTransporterCount,
        BigDecimal totalCost,
        String status,
        String optimizationObjective,
        int lanesCovered,
        String createdBy,
        Instant createdAt,
        List<Object> selectedTransporters,
        List<AssignmentItemResponse> assignments,
        List<AssignmentItemResponse> laneAssignments
) {
    public static AssignmentRunResponse from(
            Long id,
            int requestedMaxTransporters,
            int selectedTransporterCount,
            BigDecimal totalCost,
            String status,
            String optimizationObjective,
            int lanesCovered,
            String createdBy,
            Instant createdAt,
            List<Object> selectedTransporters,
            List<AssignmentItemResponse> items
    ) {
        return new AssignmentRunResponse(
                id,
                id,
                requestedMaxTransporters,
                selectedTransporterCount,
                totalCost,
                status,
                optimizationObjective,
                lanesCovered,
                createdBy,
                createdAt,
                selectedTransporters,
                items,
                items
        );
    }
}
