package com.freightfox.csv;

import com.freightfox.exception.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/import")
@Tag(name = "CSV Import", description = "Endpoints for importing transporter assignment datasets (Admin only)")
@SecurityRequirement(name = "bearerAuth")
public class CsvImportController {

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import CSV dataset", description = "Uploads and imports a CSV file containing trade lanes, transporters, and quotes (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV successfully parsed and persisted"),
            @ApiResponse(responseCode = "400", description = "Invalid CSV syntax, header, or cell values"),
            @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
            @ApiResponse(responseCode = "429", description = "Import rate limit exceeded")
    })
    public ResponseEntity<CsvImportSummary> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "strictMode", defaultValue = "true") boolean strictMode
    ) {
        if (file.isEmpty()) {
            throw new InvalidInputException("Uploaded CSV file must not be empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".csv")) {
            throw new InvalidInputException("Only .csv files are supported. Received: " + originalFilename);
        }

        try {
            CsvImportSummary summary = csvImportService.importCsv(file.getInputStream(), strictMode);
            if ("FAILED".equals(summary.status())) {
                return ResponseEntity.badRequest().body(summary);
            }
            return ResponseEntity.ok(summary);
        } catch (IOException e) {
            throw new InvalidInputException("Failed to read uploaded file: " + e.getMessage());
        }
    }
}
