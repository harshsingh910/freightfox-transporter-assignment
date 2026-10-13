package com.freightfox.csv;

import java.util.List;

/**
 * Summary returned upon completing a CSV import job.
 */
public record CsvImportSummary(
        String status,
        int lanesImported,
        int transportersImported,
        int quotesImported,
        int totalRowsProcessed,
        List<String> warnings,
        List<String> errors,
        String message
) {
    public static CsvImportSummary success(
            int lanes,
            int transporters,
            int quotes,
            int rows,
            List<String> warnings,
            String message
    ) {
        return new CsvImportSummary("SUCCESS", lanes, transporters, quotes, rows, warnings, List.of(), message);
    }

    public static CsvImportSummary failure(
            List<String> errors,
            int rows,
            String message
    ) {
        return new CsvImportSummary("FAILED", 0, 0, 0, rows, List.of(), errors, message);
    }
}
