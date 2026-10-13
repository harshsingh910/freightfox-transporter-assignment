package com.freightfox.assignment.controller;

import com.freightfox.assignment.dto.AssignmentRequest;
import com.freightfox.assignment.dto.AssignmentResponse;
import com.freightfox.assignment.dto.AssignmentRunResponse;
import com.freightfox.assignment.dto.TransportersInputRequest;
import com.freightfox.assignment.dto.TransportersInputResponse;
import com.freightfox.assignment.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transporters & Assignments", description = "Core endpoints for transporter quote input and optimization")
@SecurityRequirement(name = "bearerAuth")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/transporters/input")
    @Operation(summary = "Submit input data", description = "Submits lanes, transporters, and lane quotes in JSON format (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Input data saved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payload or validation errors"),
            @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<TransportersInputResponse> submitInputData(
            @Valid @RequestBody TransportersInputRequest request
    ) {
        TransportersInputResponse response = assignmentService.saveInputData(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transporters/assignment")
    @Operation(summary = "Get optimized assignment", description = "Solves the Transporter Assignment on Lanes optimization problem with K transporter limit")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Optimal assignment calculated and saved"),
            @ApiResponse(responseCode = "400", description = "Invalid maxTransporters constraint"),
            @ApiResponse(responseCode = "422", description = "Infeasible assignment (coverage cannot be satisfied with K transporters)"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<AssignmentResponse> getOptimizedAssignment(
            @Valid @RequestBody AssignmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = userDetails != null ? userDetails.getUsername() : "anonymous";
        AssignmentResponse response = assignmentService.computeAssignment(request, username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/assignments/{id}")
    @Operation(summary = "Get assignment run by ID", description = "Retrieves stored assignment run and individual lane assignments")
    public ResponseEntity<AssignmentResponse> getAssignmentById(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.getAssignmentRunById(id));
    }

    @GetMapping("/assignments")
    @Operation(summary = "List historical assignment runs", description = "Paginated list of historical optimization runs")
    public ResponseEntity<Page<AssignmentRunResponse>> listAssignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int boundedSize = Math.min(Math.max(1, size), 50);
        Page<AssignmentRunResponse> runs = assignmentService.getAssignmentRunsPage(PageRequest.of(Math.max(0, page), boundedSize));
        return ResponseEntity.ok(runs);
    }
}
