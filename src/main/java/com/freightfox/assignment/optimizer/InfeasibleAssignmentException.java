package com.freightfox.assignment.optimizer;

/**
 * Exception thrown when no feasible assignment can satisfy all lane coverage and transporter constraints.
 */
public class InfeasibleAssignmentException extends RuntimeException {

    private final String reason;

    public InfeasibleAssignmentException(String message) {
        super(message);
        this.reason = message;
    }

    public String getReason() {
        return reason;
    }
}
