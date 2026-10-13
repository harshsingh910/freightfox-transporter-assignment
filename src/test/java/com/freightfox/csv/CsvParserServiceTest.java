package com.freightfox.csv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CsvParserService Unit Tests")
class CsvParserServiceTest {

    private CsvParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new CsvParserService();
    }

    @Test
    @DisplayName("Should successfully parse the actual transporter_assignment_data.csv file")
    void testParseRealFile() {
        InputStream is = getClass().getResourceAsStream("/data/transporter_assignment_data.csv");
        assertNotNull(is, "transporter_assignment_data.csv should exist on classpath");

        CsvParseResult result = parserService.parse(is, true);

        assertTrue(result.valid(), "Result should be valid: " + result.errors());
        assertEquals(9, result.lanes().size(), "Should have 9 lanes");
        assertEquals(7, result.transporters().size(), "Should have 7 transporters (T1..T7)");
        assertEquals(63, result.quotes().size(), "Should have 63 quotes (9 * 7)");
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should detect duplicate transporter headers")
    void testDuplicateTransporterHeader() {
        String csv = """
                Trade Lane,T1,T2,T1
                Lane 1,100,200,300
                """;
        CsvParseResult result = parserService.parse(new StringReader(csv), true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Duplicate transporter header")));
    }

    @Test
    @DisplayName("Should detect duplicate trade lane rows")
    void testDuplicateLaneRows() {
        String csv = """
                Trade Lane,T1,T2
                Lane 1,100,200
                Lane 1,150,250
                """;
        CsvParseResult result = parserService.parse(new StringReader(csv), true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Duplicate Trade Lane")));
    }

    @Test
    @DisplayName("Should detect non-numeric and negative quotes")
    void testInvalidQuotes() {
        String csv = """
                Trade Lane,T1,T2
                Lane 1,-50,abc
                """;
        CsvParseResult result = parserService.parse(new StringReader(csv), true);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("must be positive")));
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Non-numeric quote")));
    }

    @Test
    @DisplayName("Should skip blank rows without failing")
    void testBlankRowsSkipped() {
        String csv = """
                Trade Lane,T1,T2
                Lane 1,100,200
                
                   ,   ,   
                Lane 2,300,400
                """;
        CsvParseResult result = parserService.parse(new StringReader(csv), true);

        assertTrue(result.valid());
        assertEquals(2, result.lanes().size());
        assertEquals(4, result.quotes().size());
    }

    @Test
    @DisplayName("Should reject empty CSV")
    void testEmptyCsv() {
        CsvParseResult result = parserService.parse(new StringReader(""), true);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("empty")));
    }
}
