package com.freightfox.health;

import com.freightfox.ratelimit.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Platform and dependency health checks")
public class HealthController {

    private final DataSource dataSource;
    private final RateLimitService rateLimitService;

    public HealthController(DataSource dataSource, RateLimitService rateLimitService) {
        this.dataSource = dataSource;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    @Operation(summary = "Application health check", description = "Verifies backend status, database connectivity, and Redis connectivity")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", Instant.now().toString());
        status.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        status.put("version", "1.0.0");

        Map<String, String> dependencies = new HashMap<>();

        // Check Database
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            dependencies.put("database", valid ? "UP" : "DOWN");
        } catch (Exception e) {
            dependencies.put("database", "DOWN (" + e.getMessage() + ")");
        }

        // Check Redis
        boolean redisUp = rateLimitService.isRedisConnected();
        dependencies.put("redis", redisUp ? "UP" : "DOWN (using safe in-memory fallback)");

        status.put("dependencies", dependencies);
        return ResponseEntity.ok(status);
    }
}
