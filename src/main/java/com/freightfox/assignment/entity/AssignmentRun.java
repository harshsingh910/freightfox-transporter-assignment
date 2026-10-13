package com.freightfox.assignment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assignment_runs")
public class AssignmentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_max_transporters", nullable = false)
    private int requestedMaxTransporters;

    @Column(name = "selected_transporter_count", nullable = false)
    private int selectedTransporterCount;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "optimization_objective", nullable = false, length = 64)
    private String optimizationObjective;

    @Column(name = "lanes_covered", nullable = false)
    private int lanesCovered;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JsonIgnore
    @OneToMany(mappedBy = "assignmentRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LaneAssignment> laneAssignments = new ArrayList<>();

    public AssignmentRun() {}

    public AssignmentRun(
            int requestedMaxTransporters,
            int selectedTransporterCount,
            BigDecimal totalCost,
            String status,
            String optimizationObjective,
            int lanesCovered,
            String createdBy
    ) {
        this.requestedMaxTransporters = requestedMaxTransporters;
        this.selectedTransporterCount = selectedTransporterCount;
        this.totalCost = totalCost;
        this.status = status;
        this.optimizationObjective = optimizationObjective;
        this.lanesCovered = lanesCovered;
        this.createdBy = createdBy;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void addAssignment(LaneAssignment assignment) {
        laneAssignments.add(assignment);
        assignment.setAssignmentRun(this);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getRequestedMaxTransporters() {
        return requestedMaxTransporters;
    }

    public void setRequestedMaxTransporters(int requestedMaxTransporters) {
        this.requestedMaxTransporters = requestedMaxTransporters;
    }

    public int getSelectedTransporterCount() {
        return selectedTransporterCount;
    }

    public void setSelectedTransporterCount(int selectedTransporterCount) {
        this.selectedTransporterCount = selectedTransporterCount;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOptimizationObjective() {
        return optimizationObjective;
    }

    public void setOptimizationObjective(String optimizationObjective) {
        this.optimizationObjective = optimizationObjective;
    }

    public int getLanesCovered() {
        return lanesCovered;
    }

    public void setLanesCovered(int lanesCovered) {
        this.lanesCovered = lanesCovered;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<LaneAssignment> getLaneAssignments() {
        return laneAssignments;
    }

    public void setLaneAssignments(List<LaneAssignment> laneAssignments) {
        this.laneAssignments = laneAssignments;
    }
}
