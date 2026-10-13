package com.freightfox.assignment.optimizer;

/**
 * Objective functions supported by the Transporter Assignment Optimizer.
 */
public enum OptimizationObjective {

    /**
     * Lexicographic multi-objective optimization (FreightFox hiring assignment default):
     * 1. Maximize distinct transporters used (up to K).
     * 2. Among assignments achieving that maximum feasible transporter count, minimize total cost.
     * 3. Deterministic tie-breaking on transporter IDs and lane allocations.
     */
    LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST,

    /**
     * Pure cost minimization across any feasible subset of size &lt;= K:
     * 1. Minimize total transportation cost.
     * 2. Tie-break deterministically.
     */
    MIN_COST
}
