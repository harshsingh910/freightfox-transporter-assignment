package com.freightfox.csv;

import com.freightfox.assignment.optimizer.OptimizationLane;
import com.freightfox.assignment.optimizer.OptimizationQuote;
import com.freightfox.assignment.optimizer.OptimizationTransporter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for parsing and validating transporter quotes CSV datasets.
 */
@Service
public class CsvParserService {

    private static final Logger log = LoggerFactory.getLogger(CsvParserService.class);

    public CsvParseResult parse(InputStream inputStream, boolean strictMissingQuotes) {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return parse(reader, strictMissingQuotes);
        } catch (Exception e) {
            log.error("Failed to read CSV input stream", e);
            return new CsvParseResult(
                    false,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("CSV read error: " + e.getMessage()),
                    List.of(),
                    0
            );
        }
    }

    public CsvParseResult parse(Reader reader, boolean strictMissingQuotes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<OptimizationLane> lanes = new ArrayList<>();
        List<OptimizationTransporter> transporters = new ArrayList<>();
        List<OptimizationQuote> quotes = new ArrayList<>();

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (CSVParser parser = csvFormat.parse(reader)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                errors.add("The CSV file is empty.");
                return new CsvParseResult(false, lanes, transporters, quotes, errors, warnings, 0);
            }

            // 1. Validate Header Row
            CSVRecord headerRecord = records.get(0);
            if (headerRecord.size() < 2) {
                errors.add("Header must contain at least 'Trade Lane' and one transporter column. Found " + headerRecord.size() + " columns.");
                return new CsvParseResult(false, lanes, transporters, quotes, errors, warnings, 1);
            }

            String laneHeader = headerRecord.get(0).trim();
            if (laneHeader.isEmpty()) {
                errors.add("First column header (Trade Lane) cannot be empty.");
            }

            // Transporters from headers
            Set<String> uniqueTransporters = new HashSet<>();
            List<String> transporterNames = new ArrayList<>();
            for (int col = 1; col < headerRecord.size(); col++) {
                String tName = headerRecord.get(col).trim();
                if (tName.isEmpty()) {
                    errors.add("Transporter column header at position " + (col + 1) + " cannot be blank.");
                    continue;
                }
                if (!uniqueTransporters.add(tName.toUpperCase())) {
                    errors.add("Duplicate transporter header detected: '" + tName + "' at column " + (col + 1));
                } else {
                    transporterNames.add(tName);
                    transporters.add(new OptimizationTransporter(tName, tName));
                }
            }

            if (transporters.isEmpty()) {
                errors.add("No valid transporter columns found in CSV header.");
                return new CsvParseResult(false, lanes, transporters, quotes, errors, warnings, 1);
            }

            // 2. Validate Data Rows
            Set<String> uniqueLaneNames = new HashSet<>();
            int dataRowCount = 0;

            for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
                CSVRecord record = records.get(rowIndex);

                // Check for blank row
                boolean allBlank = true;
                for (int i = 0; i < record.size(); i++) {
                    if (!record.get(i).trim().isEmpty()) {
                        allBlank = false;
                        break;
                    }
                }
                if (allBlank) {
                    warnings.add("Row " + (rowIndex + 1) + " is empty; skipped.");
                    continue;
                }

                dataRowCount++;
                String laneName = record.get(0).trim();
                if (laneName.isEmpty()) {
                    errors.add("Row " + (rowIndex + 1) + ": Trade Lane identifier is missing.");
                    continue;
                }

                if (!uniqueLaneNames.add(laneName.toUpperCase())) {
                    errors.add("Row " + (rowIndex + 1) + ": Duplicate Trade Lane '" + laneName + "' detected.");
                    continue;
                }

                // Origin and Destination mapping
                String origin = laneName;
                String destination = laneName;
                if (laneName.contains("->")) {
                    String[] parts = laneName.split("->", 2);
                    origin = parts[0].trim();
                    destination = parts[1].trim();
                } else if (laneName.contains("-") && !laneName.toLowerCase().startsWith("lane-")) {
                    String[] parts = laneName.split("-", 2);
                    origin = parts[0].trim();
                    destination = parts[1].trim();
                }

                lanes.add(new OptimizationLane(laneName, origin, destination));

                // Process quotes for each transporter
                for (int col = 1; col <= transporterNames.size(); col++) {
                    String tName = transporterNames.get(col - 1);
                    if (col >= record.size()) {
                        if (strictMissingQuotes) {
                            errors.add("Row " + (rowIndex + 1) + ": Missing column for transporter '" + tName + "'.");
                        } else {
                            warnings.add("Row " + (rowIndex + 1) + ": Missing quote for transporter '" + tName + "'.");
                        }
                        continue;
                    }

                    String rawVal = record.get(col).trim();
                    if (rawVal.isEmpty()) {
                        if (strictMissingQuotes) {
                            errors.add("Row " + (rowIndex + 1) + ": Empty quote for transporter '" + tName + "'.");
                        } else {
                            warnings.add("Row " + (rowIndex + 1) + ": Empty quote for transporter '" + tName + "'; skipped.");
                        }
                        continue;
                    }

                    try {
                        BigDecimal quoteAmount = new BigDecimal(rawVal);
                        if (quoteAmount.compareTo(BigDecimal.ZERO) <= 0) {
                            errors.add("Row " + (rowIndex + 1) + ": Quote for '" + tName + "' must be positive. Found: " + rawVal);
                        } else {
                            quotes.add(new OptimizationQuote(laneName, tName, quoteAmount));
                        }
                    } catch (NumberFormatException nfe) {
                        errors.add("Row " + (rowIndex + 1) + ": Non-numeric quote '" + rawVal + "' for transporter '" + tName + "'.");
                    }
                }
            }

            if (lanes.isEmpty() && errors.isEmpty()) {
                errors.add("CSV contains no valid lane data rows.");
            }

            boolean isValid = errors.isEmpty();
            return new CsvParseResult(
                    isValid,
                    lanes,
                    transporters,
                    quotes,
                    errors,
                    warnings,
                    records.size()
            );

        } catch (Exception e) {
            log.error("CSV parsing failed", e);
            errors.add("CSV parsing syntax error: " + e.getMessage());
            return new CsvParseResult(false, lanes, transporters, quotes, errors, warnings, 0);
        }
    }
}
