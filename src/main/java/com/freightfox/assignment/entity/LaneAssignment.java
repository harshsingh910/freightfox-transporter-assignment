package com.freightfox.assignment.entity;

import com.freightfox.lane.Lane;
import com.freightfox.transporter.Transporter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "lane_assignments")
public class LaneAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_run_id", nullable = false)
    private AssignmentRun assignmentRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false)
    private Transporter transporter;

    @Column(name = "quote_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal quoteAmount;

    public LaneAssignment() {}

    public LaneAssignment(Lane lane, Transporter transporter, BigDecimal quoteAmount) {
        this.lane = lane;
        this.transporter = transporter;
        this.quoteAmount = quoteAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AssignmentRun getAssignmentRun() {
        return assignmentRun;
    }

    public void setAssignmentRun(AssignmentRun assignmentRun) {
        this.assignmentRun = assignmentRun;
    }

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public Transporter getTransporter() {
        return transporter;
    }

    public void setTransporter(Transporter transporter) {
        this.transporter = transporter;
    }

    public BigDecimal getQuoteAmount() {
        return quoteAmount;
    }

    public void setQuoteAmount(BigDecimal quoteAmount) {
        this.quoteAmount = quoteAmount;
    }
}
