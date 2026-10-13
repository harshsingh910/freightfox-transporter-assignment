package com.freightfox.transporter;

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
@RequestMapping("/api/v1/transporters")
@Tag(name = "Transporters", description = "Query endpoints for transporters")
@SecurityRequirement(name = "bearerAuth")
public class TransporterController {

    private final TransporterService transporterService;

    public TransporterController(TransporterService transporterService) {
        this.transporterService = transporterService;
    }

    @GetMapping
    @Operation(summary = "List transporters", description = "Retrieves a paginated list of registered transporters")
    public ResponseEntity<Page<TransporterDto>> listTransporters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir
    ) {
        int boundedSize = Math.min(Math.max(1, size), 100);
        Sort sort = Sort.by("DESC".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Page<TransporterDto> result = transporterService.getTransportersPage(PageRequest.of(Math.max(0, page), boundedSize, sort));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transporter by ID", description = "Retrieves transporter details by internal ID")
    public ResponseEntity<TransporterDto> getTransporter(@PathVariable Long id) {
        return ResponseEntity.ok(transporterService.getTransporterById(id));
    }
}
