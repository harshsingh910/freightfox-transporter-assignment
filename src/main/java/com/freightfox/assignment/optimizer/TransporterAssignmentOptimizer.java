package com.freightfox.assignment.optimizer;

/**
 * Contract for optimization algorithms that solve the Transporter Assignment on Lanes problem.
 */
public interface TransporterAssignmentOptimizer {

    /**
     * Executes optimization for the given problem formulation.
     *
     * @param problem the complete problem definition including lanes, transporters, quotes, and constraints
     * @return the verified optimal solution or infeasibility outcome
     */
    OptimizationResult optimize(OptimizationProblem problem);
}
