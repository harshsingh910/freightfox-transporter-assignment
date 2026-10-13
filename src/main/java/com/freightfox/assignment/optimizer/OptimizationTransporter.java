package com.freightfox.assignment.optimizer;

import java.util.Objects;

/**
 * Domain record representing a transporter candidate in the optimization engine.
 */
public record OptimizationTransporter(
        String id,
        String name
) {
    public OptimizationTransporter {
        Objects.requireNonNull(id, "Transporter ID must not be null");
        Objects.requireNonNull(name, "Transporter name must not be null");
    }
}
