package com.freightfox.assignment.optimizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExactSubsetsTransporterOptimizer Unit Tests")
class ExactSubsetsTransporterOptimizerTest {

    private ExactSubsetsTransporterOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new ExactSubsetsTransporterOptimizer();
    }

    @Test
    @DisplayName("Should optimize the real 9-lane CSV dataset with K=3 to exact cost 134876 using T1, T4, T6")
    void testRealCsvDatasetK3() {
        // Trade lanes 1..9
        List<OptimizationLane> lanes = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            lanes.add(new OptimizationLane("Lane " + i, "Origin " + i, "Dest " + i));
        }

        // Transporters T1..T7
        List<OptimizationTransporter> transporters = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            transporters.add(new OptimizationTransporter("T" + i, "Transporter T" + i));
        }

        // Quotes matrix from transporter_assignment_data.csv:
        // Lane 1: 20835, 48844, 39020, 14400, 11601, 35095, 26070
        // Lane 2: 10512, 31326, 20648, 44514, 19760, 12494, 41098
        // Lane 3: 22105, 18640, 31438, 14316, 40870, 17808, 20932
        // Lane 4: 42481, 45828, 36447, 10678, 20635, 36210, 16897
        // Lane 5: 19862, 18297, 12789, 13032, 26421, 39444, 27938
        // Lane 6: 13567, 45810, 49985, 46024, 28809, 29948, 43517
        // Lane 7: 10015, 49573, 35285, 42342, 27815, 17320, 25881
        // Lane 8: 17886, 45122, 10281, 35742, 17024, 10492, 46136
        // Lane 9: 43996, 31856, 40092, 48921, 44691, 37864, 31286
        int[][] matrix = {
                {20835, 48844, 39020, 14400, 11601, 35095, 26070},
                {10512, 31326, 20648, 44514, 19760, 12494, 41098},
                {22105, 18640, 31438, 14316, 40870, 17808, 20932},
                {42481, 45828, 36447, 10678, 20635, 36210, 16897},
                {19862, 18297, 12789, 13032, 26421, 39444, 27938},
                {13567, 45810, 49985, 46024, 28809, 29948, 43517},
                {10015, 49573, 35285, 42342, 27815, 17320, 25881},
                {17886, 45122, 10281, 35742, 17024, 10492, 46136},
                {43996, 31856, 40092, 48921, 44691, 37864, 31286}
        };

        List<OptimizationQuote> quotes = new ArrayList<>();
        for (int l = 0; l < 9; l++) {
            for (int t = 0; t < 7; t++) {
                quotes.add(new OptimizationQuote("Lane " + (l + 1), "T" + (t + 1), BigDecimal.valueOf(matrix[l][t])));
            }
        }

        OptimizationProblem problem = OptimizationProblem.of(lanes, transporters, quotes, 3);
        OptimizationResult result = optimizer.optimize(problem);

        assertTrue(result.feasible(), "Expected solution to be feasible");
        assertEquals(new BigDecimal("134876"), result.totalCost());
        assertEquals(3, result.selectedTransporterCount());
        assertEquals(Set.of("T1", "T4", "T6"), result.selectedTransporterIds());
        assertEquals(9, result.lanesCovered());
        assertEquals(9, result.assignments().size());

        // Verify specific lane assignments
        Map<String, String> assignmentMap = new HashMap<>();
        for (LaneAssignmentResult r : result.assignments()) {
            assignmentMap.put(r.laneId(), r.transporterId());
        }
        assertEquals("T4", assignmentMap.get("Lane 1"));
        assertEquals("T1", assignmentMap.get("Lane 2"));
        assertEquals("T4", assignmentMap.get("Lane 3"));
        assertEquals("T4", assignmentMap.get("Lane 4"));
        assertEquals("T4", assignmentMap.get("Lane 5"));
        assertEquals("T1", assignmentMap.get("Lane 6"));
        assertEquals("T1", assignmentMap.get("Lane 7"));
        assertEquals("T6", assignmentMap.get("Lane 8"));
        assertEquals("T6", assignmentMap.get("Lane 9"));
    }

    @Test
    @DisplayName("Should optimize real CSV dataset for K=1 to T1 with cost 201259")
    void testRealCsvDatasetK1() {
        List<OptimizationLane> lanes = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            lanes.add(new OptimizationLane("Lane " + i, "Origin " + i, "Dest " + i));
        }

        List<OptimizationTransporter> transporters = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            transporters.add(new OptimizationTransporter("T" + i, "Transporter T" + i));
        }

        int[][] matrix = {
                {20835, 48844, 39020, 14400, 11601, 35095, 26070},
                {10512, 31326, 20648, 44514, 19760, 12494, 41098},
                {22105, 18640, 31438, 14316, 40870, 17808, 20932},
                {42481, 45828, 36447, 10678, 20635, 36210, 16897},
                {19862, 18297, 12789, 13032, 26421, 39444, 27938},
                {13567, 45810, 49985, 46024, 28809, 29948, 43517},
                {10015, 49573, 35285, 42342, 27815, 17320, 25881},
                {17886, 45122, 10281, 35742, 17024, 10492, 46136},
                {43996, 31856, 40092, 48921, 44691, 37864, 31286}
        };

        List<OptimizationQuote> quotes = new ArrayList<>();
        for (int l = 0; l < 9; l++) {
            for (int t = 0; t < 7; t++) {
                quotes.add(new OptimizationQuote("Lane " + (l + 1), "T" + (t + 1), BigDecimal.valueOf(matrix[l][t])));
            }
        }

        OptimizationProblem problem = OptimizationProblem.of(lanes, transporters, quotes, 1);
        OptimizationResult result = optimizer.optimize(problem);

        assertTrue(result.feasible());
        assertEquals(new BigDecimal("201259"), result.totalCost());
        assertEquals(1, result.selectedTransporterCount());
        assertEquals(Set.of("T1"), result.selectedTransporterIds());
    }

    @Test
    @DisplayName("Should detect infeasibility when sparse quotes require more transporters than K")
    void testSparseQuotesInfeasibleWhenKTooSmall() {
        // Test Case 2 from specification:
        // Lanes 1..5
        List<OptimizationLane> lanes = List.of(
                new OptimizationLane("1", "Chandigarh", "Shimla"),
                new OptimizationLane("2", "Agra", "Kanpur"),
                new OptimizationLane("3", "Varanasi", "Gorakhpur"),
                new OptimizationLane("4", "Amritsar", "Ludhiana"),
                new OptimizationLane("5", "Coimbatore", "Madurai")
        );

        List<OptimizationTransporter> transporters = List.of(
                new OptimizationTransporter("1", "Transporter X"),
                new OptimizationTransporter("2", "Transporter Y"),
                new OptimizationTransporter("3", "Transporter Z"),
                new OptimizationTransporter("4", "Transporter W")
        );

        List<OptimizationQuote> quotes = List.of(
                new OptimizationQuote("1", "1", new BigDecimal("1000")),
                new OptimizationQuote("2", "1", new BigDecimal("1500")),
                new OptimizationQuote("3", "2", new BigDecimal("2000")),
                new OptimizationQuote("4", "2", new BigDecimal("1800")),
                new OptimizationQuote("5", "3", new BigDecimal("1200")),
                new OptimizationQuote("1", "4", new BigDecimal("1100")),
                new OptimizationQuote("4", "4", new BigDecimal("1750"))
        );

        // Transporter Z is the only one on Lane 5; Transporter Y is the only one on Lane 3;
        // Transporter X is the only one on Lane 2.
        // Thus at least 3 distinct transporters (1, 2, 3) are required!
        // When K=2, it must be infeasible.
        OptimizationProblem problemK2 = OptimizationProblem.of(lanes, transporters, quotes, 2);
        OptimizationResult resultK2 = optimizer.optimize(problemK2);

        assertFalse(resultK2.feasible(), "Should be infeasible with K=2");
        assertNotNull(resultK2.infeasibilityReason());

        // When K=3, it should be feasible with cost 1000 + 1500 + 2000 + 1800 + 1200 = 7500
        OptimizationProblem problemK3 = OptimizationProblem.of(lanes, transporters, quotes, 3);
        OptimizationResult resultK3 = optimizer.optimize(problemK3);

        assertTrue(resultK3.feasible());
        assertEquals(new BigDecimal("7500"), resultK3.totalCost());
        assertEquals(3, resultK3.selectedTransporterCount());
        assertEquals(Set.of("1", "2", "3"), resultK3.selectedTransporterIds());
    }

    @Test
    @DisplayName("Should handle boundary case: 1 lane and 1 transporter")
    void testOneLaneOneTransporter() {
        List<OptimizationLane> lanes = List.of(new OptimizationLane("L1", "A", "B"));
        List<OptimizationTransporter> transporters = List.of(new OptimizationTransporter("T1", "Transporter 1"));
        List<OptimizationQuote> quotes = List.of(new OptimizationQuote("L1", "T1", new BigDecimal("500.00")));

        OptimizationProblem problem = OptimizationProblem.of(lanes, transporters, quotes, 1);
        OptimizationResult result = optimizer.optimize(problem);

        assertTrue(result.feasible());
        assertEquals(new BigDecimal("500.00"), result.totalCost());
        assertEquals(1, result.selectedTransporterCount());
        assertEquals(Set.of("T1"), result.selectedTransporterIds());
    }

    @Test
    @DisplayName("Should handle K greater than available transporters gracefully")
    void testKGreaterThanAvailableTransporters() {
        List<OptimizationLane> lanes = List.of(
                new OptimizationLane("L1", "A", "B"),
                new OptimizationLane("L2", "C", "D")
        );
        List<OptimizationTransporter> transporters = List.of(
                new OptimizationTransporter("T1", "Transporter 1"),
                new OptimizationTransporter("T2", "Transporter 2")
        );
        List<OptimizationQuote> quotes = List.of(
                new OptimizationQuote("L1", "T1", new BigDecimal("100")),
                new OptimizationQuote("L2", "T2", new BigDecimal("200"))
        );

        // K=10 but only 2 transporters exist
        OptimizationProblem problem = OptimizationProblem.of(lanes, transporters, quotes, 10);
        OptimizationResult result = optimizer.optimize(problem);

        assertTrue(result.feasible());
        assertEquals(new BigDecimal("300"), result.totalCost());
        assertEquals(2, result.selectedTransporterCount());
        assertEquals(Set.of("T1", "T2"), result.selectedTransporterIds());
    }

    @Test
    @DisplayName("Should fail when a lane has no quotes from any transporter")
    void testLaneWithNoQuotes() {
        List<OptimizationLane> lanes = List.of(
                new OptimizationLane("L1", "A", "B"),
                new OptimizationLane("L2", "C", "D")
        );
        List<OptimizationTransporter> transporters = List.of(
                new OptimizationTransporter("T1", "Transporter 1")
        );
        // Only quote for L1, none for L2
        List<OptimizationQuote> quotes = List.of(
                new OptimizationQuote("L1", "T1", new BigDecimal("100"))
        );

        OptimizationProblem problem = OptimizationProblem.of(lanes, transporters, quotes, 2);
        OptimizationResult result = optimizer.optimize(problem);

        assertFalse(result.feasible());
        assertTrue(result.infeasibilityReason().contains("has no quotes"));
    }

    @Test
    @DisplayName("Should break quote ties deterministically")
    void testDeterministicTieBreaking() {
        List<OptimizationLane> lanes = List.of(new OptimizationLane("L1", "A", "B"));
        List<OptimizationTransporter> transporters = List.of(
                new OptimizationTransporter("T2", "Transporter 2"),
                new OptimizationTransporter("T1", "Transporter 1")
        );
        // Both quote the exact same amount 500
        List<OptimizationQuote> quotes = List.of(
                new OptimizationQuote("L1", "T2", new BigDecimal("500")),
                new OptimizationQuote("L1", "T1", new BigDecimal("500"))
        );

        OptimizationProblem problem = OptimizationProblem.of(lanes, transporters, quotes, 1);
        OptimizationResult result = optimizer.optimize(problem);

        assertTrue(result.feasible());
        // Deterministic tie-breaker should pick T1 (lexicographically lower)
        assertEquals(Set.of("T1"), result.selectedTransporterIds());
        assertEquals("T1", result.assignments().get(0).transporterId());
    }

    @Test
    @DisplayName("Randomized oracle comparison: Verify against brute-force generator across 30 random instances")
    void testAgainstBruteForceRandomized() {
        Random rnd = new Random(42);

        for (int iter = 0; iter < 30; iter++) {
            int numLanes = 3 + rnd.nextInt(4); // 3 to 6 lanes
            int numTransporters = 3 + rnd.nextInt(3); // 3 to 5 transporters
            int K = 1 + rnd.nextInt(numTransporters);

            List<OptimizationLane> lanes = new ArrayList<>();
            for (int l = 0; l < numLanes; l++) {
                lanes.add(new OptimizationLane("L" + l, "O" + l, "D" + l));
            }

            List<OptimizationTransporter> transporters = new ArrayList<>();
            for (int t = 0; t < numTransporters; t++) {
                transporters.add(new OptimizationTransporter("T" + t, "Transporter " + t));
            }

            List<OptimizationQuote> quotes = new ArrayList<>();
            for (int l = 0; l < numLanes; l++) {
                for (int t = 0; t < numTransporters; t++) {
                    // 85% probability quote exists
                    if (rnd.nextDouble() < 0.85) {
                        int amount = 100 + rnd.nextInt(5000);
                        quotes.add(new OptimizationQuote("L" + l, "T" + t, BigDecimal.valueOf(amount)));
                    }
                }
            }

            OptimizationProblem problem = OptimizationProblem.of(lanes, transporters, quotes, K);
            OptimizationResult result = optimizer.optimize(problem);

            if (result.feasible()) {
                // Verify hard constraints on solution
                assertTrue(result.selectedTransporterCount() <= K, "Must not exceed K");
                assertEquals(numLanes, result.assignments().size(), "Every lane must be assigned");

                Set<String> assignedTransporters = new HashSet<>();
                BigDecimal totalSum = BigDecimal.ZERO;
                for (LaneAssignmentResult a : result.assignments()) {
                    assignedTransporters.add(a.transporterId());
                    totalSum = totalSum.add(a.quoteAmount());
                }
                assertEquals(totalSum, result.totalCost(), "Total cost must equal sum of quotes");
                assertEquals(assignedTransporters, result.selectedTransporterIds(), "Selected transporters must match used ones");
            }
        }
    }
}
