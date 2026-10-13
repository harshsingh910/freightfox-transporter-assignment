package com.freightfox.assignment.optimizer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Exact combinatorial optimization engine for Transporter Assignment on Lanes.
 * <p>
 * Given N lanes, M transporters, a matrix of positive quote amounts, and a maximum
 * transporter limit K:
 * <ul>
 *     <li>Hard Constraint 1: Every lane must be assigned to exactly one selected transporter.</li>
 *     <li>Hard Constraint 2: No unselected transporter receives an assignment.</li>
 *     <li>Hard Constraint 3: At most K distinct transporters are selected in the solution.</li>
 *     <li>Hard Constraint 4: Only valid existing quotes are utilized.</li>
 * </ul>
 * <p>
 * Primary Lexicographic Objective:
 * <ol>
 *     <li>Maximize the number of distinct transporters used (up to K) among feasible assignments.</li>
 *     <li>Among assignments using that maximal count, minimize total transportation cost.</li>
 *     <li>Apply a deterministic tie-breaker (lexicographical ordering of transporter IDs and lane assignments).</li>
 * </ol>
 * <p>
 * This class is decoupled from Spring to facilitate direct unit and property testing.
 */
public class ExactSubsetsTransporterOptimizer implements TransporterAssignmentOptimizer {

    @Override
    public OptimizationResult optimize(OptimizationProblem problem) {
        long startTime = System.currentTimeMillis();
        Objects.requireNonNull(problem, "OptimizationProblem must not be null");

        // 1. Validate empty inputs
        if (problem.lanes().isEmpty()) {
            return OptimizationResult.infeasible(
                    "No trade lanes provided for assignment.",
                    problem.objective(),
                    System.currentTimeMillis() - startTime
            );
        }
        if (problem.transporters().isEmpty()) {
            return OptimizationResult.infeasible(
                    "No transporters available for assignment.",
                    problem.objective(),
                    System.currentTimeMillis() - startTime
            );
        }

        // 2. Index quotes by lane and transporter for O(1) rate queries
        Map<String, Map<String, BigDecimal>> quotesByLane = new HashMap<>();
        for (OptimizationLane lane : problem.lanes()) {
            quotesByLane.put(lane.id(), new HashMap<>());
        }

        Set<String> knownTransporterIds = new HashSet<>();
        for (OptimizationTransporter t : problem.transporters()) {
            knownTransporterIds.add(t.id());
        }

        for (OptimizationQuote quote : problem.quotes()) {
            if (quotesByLane.containsKey(quote.laneId()) && knownTransporterIds.contains(quote.transporterId())) {
                quotesByLane.get(quote.laneId()).put(quote.transporterId(), quote.quoteAmount());
            }
        }

        // 3. Verify that every lane has at least one valid quote across all transporters
        for (OptimizationLane lane : problem.lanes()) {
            Map<String, BigDecimal> laneQuotes = quotesByLane.get(lane.id());
            if (laneQuotes == null || laneQuotes.isEmpty()) {
                return OptimizationResult.infeasible(
                        "Lane '" + lane.id() + "' (" + lane.origin() + " -> " + lane.destination() + ") has no quotes from any transporter.",
                        problem.objective(),
                        System.currentTimeMillis() - startTime
                );
            }
        }

        // 4. Extract sorted transporter IDs for deterministic combination generation
        List<String> transporterIds = new ArrayList<>(knownTransporterIds);
        Collections.sort(transporterIds);

        int totalTransporters = transporterIds.size();
        int maxK = Math.min(problem.maxTransporters(), totalTransporters);

        // 5. Check combinatorial threshold
        BigInteger totalCombinations = calculateTotalCombinations(totalTransporters, maxK);
        if (totalCombinations.compareTo(BigInteger.valueOf(problem.maxCombinatorialSubsets())) > 0) {
            return OptimizationResult.infeasible(
                    "Problem size exceeds maximum combinatorial threshold (" + totalCombinations + " > " + problem.maxCombinatorialSubsets() + "). Consider smaller K or sub-problem partitioning.",
                    problem.objective(),
                    System.currentTimeMillis() - startTime
            );
        }

        // 6. Enumerate candidate transporter subsets of sizes 1 to maxK
        List<CandidateSolution> feasibleCandidates = new ArrayList<>();
        List<String> currentSubset = new ArrayList<>();
        long deadline = startTime + problem.maxRuntimeMillis();

        for (int k = 1; k <= maxK; k++) {
            generateCombinationsAndEvaluate(
                    transporterIds,
                    0,
                    k,
                    currentSubset,
                    problem.lanes(),
                    quotesByLane,
                    feasibleCandidates,
                    deadline
            );
        }

        long elapsed = System.currentTimeMillis() - startTime;

        if (feasibleCandidates.isEmpty()) {
            return OptimizationResult.infeasible(
                    "No combination of up to " + problem.maxTransporters() + " transporters can cover all " + problem.lanes().size() + " lanes. Minimum transporters required exceeds the limit.",
                    problem.objective(),
                    elapsed
            );
        }

        // 7. Select best candidate based on objective
        Comparator<CandidateSolution> comparator = getComparator(problem.objective());
        feasibleCandidates.sort(comparator);
        CandidateSolution best = feasibleCandidates.get(0);

        return OptimizationResult.feasible(
                best.totalCost,
                best.usedTransporters,
                problem.lanes().size(),
                best.assignments,
                problem.objective(),
                elapsed
        );
    }

    private void generateCombinationsAndEvaluate(
            List<String> allTransporters,
            int startIndex,
            int targetSize,
            List<String> currentSubset,
            List<OptimizationLane> lanes,
            Map<String, Map<String, BigDecimal>> quotesByLane,
            List<CandidateSolution> feasibleCandidates,
            long deadline
    ) {
        if (System.currentTimeMillis() > deadline) {
            throw new RuntimeException("Optimization exceeded maximum allowed runtime threshold.");
        }

        if (currentSubset.size() == targetSize) {
            evaluateSubset(currentSubset, lanes, quotesByLane, feasibleCandidates);
            return;
        }

        for (int i = startIndex; i < allTransporters.size(); i++) {
            currentSubset.add(allTransporters.get(i));
            generateCombinationsAndEvaluate(
                    allTransporters,
                    i + 1,
                    targetSize,
                    currentSubset,
                    lanes,
                    quotesByLane,
                    feasibleCandidates,
                    deadline
            );
            currentSubset.remove(currentSubset.size() - 1);
        }
    }

    private void evaluateSubset(
            List<String> subset,
            List<OptimizationLane> lanes,
            Map<String, Map<String, BigDecimal>> quotesByLane,
            List<CandidateSolution> feasibleCandidates
    ) {
        Set<String> subsetSet = new HashSet<>(subset);
        BigDecimal totalCost = BigDecimal.ZERO;
        List<LaneAssignmentResult> assignments = new ArrayList<>(lanes.size());
        Set<String> usedTransporters = new TreeSet<>();

        for (OptimizationLane lane : lanes) {
            Map<String, BigDecimal> laneQuotes = quotesByLane.get(lane.id());
            String bestTransporter = null;
            BigDecimal minQuote = null;

            for (String tId : subset) {
                BigDecimal quote = laneQuotes.get(tId);
                if (quote != null) {
                    if (minQuote == null || quote.compareTo(minQuote) < 0) {
                        minQuote = quote;
                        bestTransporter = tId;
                    } else if (quote.compareTo(minQuote) == 0 && bestTransporter != null && tId.compareTo(bestTransporter) < 0) {
                        // Deterministic tie-breaker: choose lexicographically lower transporter ID
                        bestTransporter = tId;
                    }
                }
            }

            if (bestTransporter == null || minQuote == null) {
                // Subset cannot cover this lane
                return;
            }

            totalCost = totalCost.add(minQuote);
            usedTransporters.add(bestTransporter);
            assignments.add(new LaneAssignmentResult(lane.id(), bestTransporter, minQuote));
        }

        // Successfully covered all lanes with transporters in this subset
        feasibleCandidates.add(new CandidateSolution(
                totalCost,
                usedTransporters,
                assignments
        ));
    }

    private Comparator<CandidateSolution> getComparator(OptimizationObjective objective) {
        if (objective == OptimizationObjective.MIN_COST) {
            return (a, b) -> {
                // 1. Min total cost
                int costComp = a.totalCost.compareTo(b.totalCost);
                if (costComp != 0) return costComp;
                // 2. Deterministic tie-breaker on used transporters representation
                int countComp = Integer.compare(b.usedTransporters.size(), a.usedTransporters.size());
                if (countComp != 0) return countComp;
                return compareTransporterSets(a.usedTransporters, b.usedTransporters);
            };
        }

        // Default: LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST
        return (a, b) -> {
            // 1. Max distinct transporters used
            int countComp = Integer.compare(b.usedTransporters.size(), a.usedTransporters.size());
            if (countComp != 0) return countComp;
            // 2. Min total cost
            int costComp = a.totalCost.compareTo(b.totalCost);
            if (costComp != 0) return costComp;
            // 3. Deterministic tie-breaker on used transporters set
            return compareTransporterSets(a.usedTransporters, b.usedTransporters);
        };
    }

    private int compareTransporterSets(Set<String> setA, Set<String> setB) {
        List<String> listA = new ArrayList<>(setA);
        List<String> listB = new ArrayList<>(setB);
        Collections.sort(listA);
        Collections.sort(listB);

        int minLen = Math.min(listA.size(), listB.size());
        for (int i = 0; i < minLen; i++) {
            int c = listA.get(i).compareTo(listB.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(listA.size(), listB.size());
    }

    private BigInteger calculateTotalCombinations(int n, int maxK) {
        BigInteger total = BigInteger.ZERO;
        for (int k = 1; k <= maxK; k++) {
            total = total.add(combinations(n, k));
        }
        return total;
    }

    private BigInteger combinations(int n, int k) {
        if (k < 0 || k > n) return BigInteger.ZERO;
        if (k == 0 || k == n) return BigInteger.ONE;
        BigInteger num = BigInteger.ONE;
        BigInteger den = BigInteger.ONE;
        for (int i = 1; i <= k; i++) {
            num = num.multiply(BigInteger.valueOf(n - i + 1));
            den = den.multiply(BigInteger.valueOf(i));
        }
        return num.divide(den);
    }

    private static class CandidateSolution {
        final BigDecimal totalCost;
        final Set<String> usedTransporters;
        final List<LaneAssignmentResult> assignments;

        CandidateSolution(BigDecimal totalCost, Set<String> usedTransporters, List<LaneAssignmentResult> assignments) {
            this.totalCost = totalCost;
            this.usedTransporters = usedTransporters;
            this.assignments = assignments;
        }
    }
}
