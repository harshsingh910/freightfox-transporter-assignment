package com.freightfox.assignment.service;

import com.freightfox.assignment.dto.AssignmentItemResponse;
import com.freightfox.assignment.dto.AssignmentRequest;
import com.freightfox.assignment.dto.AssignmentResponse;
import com.freightfox.assignment.dto.InputLaneDto;
import com.freightfox.assignment.dto.InputLaneQuoteDto;
import com.freightfox.assignment.dto.InputTransporterDto;
import com.freightfox.assignment.dto.TransportersInputRequest;
import com.freightfox.assignment.dto.TransportersInputResponse;
import com.freightfox.assignment.entity.AssignmentRun;
import com.freightfox.assignment.entity.LaneAssignment;
import com.freightfox.assignment.optimizer.ExactSubsetsTransporterOptimizer;
import com.freightfox.assignment.optimizer.InfeasibleAssignmentException;
import com.freightfox.assignment.optimizer.LaneAssignmentResult;
import com.freightfox.assignment.optimizer.OptimizationLane;
import com.freightfox.assignment.optimizer.OptimizationObjective;
import com.freightfox.assignment.optimizer.OptimizationProblem;
import com.freightfox.assignment.optimizer.OptimizationQuote;
import com.freightfox.assignment.optimizer.OptimizationResult;
import com.freightfox.assignment.optimizer.OptimizationTransporter;
import com.freightfox.assignment.repository.AssignmentRunRepository;
import com.freightfox.exception.InvalidInputException;
import com.freightfox.exception.ResourceNotFoundException;
import com.freightfox.lane.Lane;
import com.freightfox.lane.LaneRepository;
import com.freightfox.quote.TransporterQuote;
import com.freightfox.quote.TransporterQuoteRepository;
import com.freightfox.transporter.Transporter;
import com.freightfox.transporter.TransporterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core application service coordinating input ingestion, optimization runs, and persistence.
 */
@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final LaneRepository laneRepository;
    private final TransporterRepository transporterRepository;
    private final TransporterQuoteRepository quoteRepository;
    private final AssignmentRunRepository assignmentRunRepository;
    private final ExactSubsetsTransporterOptimizer optimizer = new ExactSubsetsTransporterOptimizer();

    @Value("${app.optimizer.default-objective:LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST}")
    private String defaultObjectiveStr;

    public AssignmentService(
            LaneRepository laneRepository,
            TransporterRepository transporterRepository,
            TransporterQuoteRepository quoteRepository,
            AssignmentRunRepository assignmentRunRepository
    ) {
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.quoteRepository = quoteRepository;
        this.assignmentRunRepository = assignmentRunRepository;
    }

    @Transactional
    @CacheEvict(value = {"lanes", "transporters", "assignments"}, allEntries = true)
    public TransportersInputResponse saveInputData(TransportersInputRequest request) {
        log.info("Processing transporter input: {} lanes, {} transporters",
                request.lanes().size(), request.transporters().size());

        // 1. Validate uniqueness of lanes in payload
        Set<String> laneExternalIds = new HashSet<>();
        for (InputLaneDto laneDto : request.lanes()) {
            String extId = laneDto.getExternalId();
            if (!laneExternalIds.add(extId)) {
                throw new InvalidInputException("Duplicate lane ID in request payload: " + extId);
            }
        }

        // 2. Validate uniqueness of transporters in payload
        Set<String> transporterExternalIds = new HashSet<>();
        for (InputTransporterDto tDto : request.transporters()) {
            String extId = tDto.getExternalId();
            if (!transporterExternalIds.add(extId)) {
                throw new InvalidInputException("Duplicate transporter ID in request payload: " + extId);
            }

            // Verify lane references in quotes
            Set<String> quotedLanes = new HashSet<>();
            for (InputLaneQuoteDto qDto : tDto.laneQuotes()) {
                String quoteLaneId = qDto.getExternalLaneId();
                if (!laneExternalIds.contains(quoteLaneId)) {
                    throw new InvalidInputException("Transporter '" + extId + "' quotes on undefined lane: " + quoteLaneId);
                }
                if (!quotedLanes.add(quoteLaneId)) {
                    throw new InvalidInputException("Transporter '" + extId + "' contains duplicate quotes for lane: " + quoteLaneId);
                }
            }
        }

        // 3. Upsert Lanes
        Map<String, Lane> savedLanes = new HashMap<>();
        for (InputLaneDto laneDto : request.lanes()) {
            String extId = laneDto.getExternalId();
            Lane lane = laneRepository.findByExternalLaneId(extId)
                    .orElseGet(() -> new Lane(extId, laneDto.origin(), laneDto.destination()));
            lane.setOrigin(laneDto.origin());
            lane.setDestination(laneDto.destination());
            savedLanes.put(extId, laneRepository.save(lane));
        }

        // 4. Upsert Transporters and Quotes
        for (InputTransporterDto tDto : request.transporters()) {
            String extId = tDto.getExternalId();
            Transporter existingTransporter = transporterRepository.findByExternalTransporterId(extId)
                    .orElseGet(() -> new Transporter(extId, tDto.name()));
            existingTransporter.setName(tDto.name());
            final Transporter savedTransporter = transporterRepository.save(existingTransporter);

            for (InputLaneQuoteDto qDto : tDto.laneQuotes()) {
                Lane lane = savedLanes.get(qDto.getExternalLaneId());
                TransporterQuote quote = quoteRepository.findByLaneAndTransporter(lane, savedTransporter)
                        .orElseGet(() -> new TransporterQuote(lane, savedTransporter, qDto.quote()));
                quote.setQuoteAmount(qDto.quote());
                quoteRepository.save(quote);
            }
        }

        log.info("Successfully persisted {} lanes and {} transporters", savedLanes.size(), request.transporters().size());
        return TransportersInputResponse.success();
    }

    @Transactional
    public AssignmentResponse computeAssignment(AssignmentRequest request, String createdBy) {
        log.info("Executing optimization request: maxTransporters={}, createdBy={}",
                request.maxTransporters(), createdBy);

        List<Lane> allLanes = laneRepository.findAll();
        List<Transporter> allTransporters = transporterRepository.findAll();
        List<TransporterQuote> allQuotes = quoteRepository.findAllWithDetails();

        if (allLanes.isEmpty()) {
            throw new InfeasibleAssignmentException("No trade lanes exist in the system. Please submit input data or import a CSV first.");
        }
        if (allTransporters.isEmpty()) {
            throw new InfeasibleAssignmentException("No transporters exist in the system. Please submit input data or import a CSV first.");
        }

        // Convert to optimizer domain models
        List<OptimizationLane> optLanes = allLanes.stream()
                .map(l -> new OptimizationLane(l.getExternalLaneId(), l.getOrigin(), l.getDestination()))
                .toList();

        List<OptimizationTransporter> optTransporters = allTransporters.stream()
                .map(t -> new OptimizationTransporter(t.getExternalTransporterId(), t.getName()))
                .toList();

        List<OptimizationQuote> optQuotes = allQuotes.stream()
                .map(q -> new OptimizationQuote(
                        q.getLane().getExternalLaneId(),
                        q.getTransporter().getExternalTransporterId(),
                        q.getQuoteAmount()
                ))
                .toList();

        OptimizationObjective objective = request.objective();
        if (objective == null) {
            objective = OptimizationObjective.valueOf(defaultObjectiveStr);
        }

        OptimizationProblem problem = new OptimizationProblem(
                optLanes,
                optTransporters,
                optQuotes,
                request.maxTransporters(),
                objective,
                OptimizationProblem.DEFAULT_MAX_COMBINATORIAL_SUBSETS,
                OptimizationProblem.DEFAULT_MAX_RUNTIME_MILLIS
        );

        OptimizationResult result = optimizer.optimize(problem);

        if (!result.feasible()) {
            log.warn("Optimization infeasible: {}", result.infeasibilityReason());
            throw new InfeasibleAssignmentException(result.infeasibilityReason());
        }

        // Persist AssignmentRun
        AssignmentRun run = new AssignmentRun(
                request.maxTransporters(),
                result.selectedTransporterCount(),
                result.totalCost(),
                "SUCCESS",
                objective.name(),
                result.lanesCovered(),
                createdBy
        );

        Map<String, Lane> laneMap = new HashMap<>();
        for (Lane l : allLanes) laneMap.put(l.getExternalLaneId(), l);

        Map<String, Transporter> transporterMap = new HashMap<>();
        for (Transporter t : allTransporters) transporterMap.put(t.getExternalTransporterId(), t);

        List<AssignmentItemResponse> itemResponses = new ArrayList<>();

        for (LaneAssignmentResult r : result.assignments()) {
            Lane lane = laneMap.get(r.laneId());
            Transporter transporter = transporterMap.get(r.transporterId());

            LaneAssignment la = new LaneAssignment(lane, transporter, r.quoteAmount());
            run.addAssignment(la);

            itemResponses.add(AssignmentItemResponse.of(
                    parseOrKeepId(r.laneId()),
                    parseOrKeepId(r.transporterId()),
                    r.quoteAmount(),
                    lane.getOrigin(),
                    lane.getDestination(),
                    transporter.getName()
            ));
        }

        AssignmentRun savedRun = assignmentRunRepository.save(run);
        log.info("Saved assignment run #{}: totalCost={}, selectedCount={}",
                savedRun.getId(), savedRun.getTotalCost(), savedRun.getSelectedTransporterCount());

        List<Object> selectedFormatted = new ArrayList<>();
        List<String> sortedSelected = new ArrayList<>(result.selectedTransporterIds());
        Collections.sort(sortedSelected);
        for (String tid : sortedSelected) {
            selectedFormatted.add(parseOrKeepId(tid));
        }

        return AssignmentResponse.success(
                savedRun.getId(),
                savedRun.getTotalCost(),
                selectedFormatted,
                savedRun.getLanesCovered(),
                objective.name(),
                itemResponses
        );
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentRunById(Long id) {
        AssignmentRun run = assignmentRunRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment run not found with ID: " + id));

        Set<String> selectedSet = new HashSet<>();
        List<AssignmentItemResponse> items = new ArrayList<>();

        for (LaneAssignment la : run.getLaneAssignments()) {
            selectedSet.add(la.getTransporter().getExternalTransporterId());
            items.add(AssignmentItemResponse.of(
                    parseOrKeepId(la.getLane().getExternalLaneId()),
                    parseOrKeepId(la.getTransporter().getExternalTransporterId()),
                    la.getQuoteAmount(),
                    la.getLane().getOrigin(),
                    la.getLane().getDestination(),
                    la.getTransporter().getName()
            ));
        }

        List<String> sortedSelected = new ArrayList<>(selectedSet);
        Collections.sort(sortedSelected);
        List<Object> selectedFormatted = sortedSelected.stream()
                .map(this::parseOrKeepId)
                .toList();

        return AssignmentResponse.success(
                run.getId(),
                run.getTotalCost(),
                selectedFormatted,
                run.getLanesCovered(),
                run.getOptimizationObjective(),
                items
        );
    }

    @Transactional(readOnly = true)
    public Page<AssignmentRun> getAssignmentRunsPage(Pageable pageable) {
        return assignmentRunRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private Object parseOrKeepId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return idStr;
        }
    }
}
