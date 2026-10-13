package com.freightfox.lane;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lanes")
@Tag(name = "Lanes", description = "Query endpoints for trade lanes")
@SecurityRequirement(name = "bearerAuth")
public class LaneController {

    private final LaneService laneService;

    public LaneController(LaneService laneService) {
        this.laneService = laneService;
    }

    @GetMapping
    @Operation(summary = "List trade lanes", description = "Retrieves a paginated list of trade lanes")
    public ResponseEntity<Page<LaneDto>> listLanes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir
    ) {
        int boundedSize = Math.min(Math.max(1, size), 100);
        Sort sort = Sort.by("DESC".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Page<LaneDto> result = laneService.getLanesPage(PageRequest.of(Math.max(0, page), boundedSize, sort));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get lane by ID", description = "Retrieves lane details by internal ID")
    public ResponseEntity<LaneDto> getLane(@PathVariable Long id) {
        return ResponseEntity.ok(laneService.getLaneById(id));
    }
}
