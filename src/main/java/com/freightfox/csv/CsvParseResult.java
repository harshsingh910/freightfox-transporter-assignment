package com.freightfox.csv;

import com.freightfox.assignment.optimizer.OptimizationLane;
import com.freightfox.assignment.optimizer.OptimizationQuote;
import com.freightfox.assignment.optimizer.OptimizationTransporter;

import java.util.List;

/**
 * Result of parsing and validating a CSV dataset.
 */
public record CsvParseResult(
        boolean valid,
        List<OptimizationLane> lanes,
        List<OptimizationTransporter> transporters,
        List<OptimizationQuote> quotes,
        List<String> errors,
        List<String> warnings,
        int totalRowsProcessed
) {
}
