package com.freightfox.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP filter enforcing distributed rate-limit quotas before application processing.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Bypass documentation, actuator, and health endpoints
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") ||
                path.startsWith("/actuator") || path.equals("/api/v1/health") || path.equals("/favicon.ico")) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitType type = determineType(request);
        String clientKey = extractClientKey(request);

        RateLimitResult result = rateLimitService.tryConsume(type, clientKey);

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));

            String traceId = MDC.get("traceId");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }

            ErrorResponse error = ErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RATE_LIMIT_EXCEEDED",
                    "Rate limit exceeded for " + type.name().toLowerCase() + " operations. Please retry in " + result.retryAfterSeconds() + " seconds.",
                    path,
                    traceId
            );

            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitType determineType(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.equals("/api/v1/auth/login") && "POST".equalsIgnoreCase(method)) {
            return RateLimitType.LOGIN;
        }
        if (path.equals("/api/v1/auth/register") && "POST".equalsIgnoreCase(method)) {
            return RateLimitType.REGISTER;
        }
        if (path.equals("/api/v1/transporters/assignment") && "POST".equalsIgnoreCase(method)) {
            return RateLimitType.ASSIGNMENT;
        }
        if (path.equals("/api/v1/import/csv") && "POST".equalsIgnoreCase(method)) {
            return RateLimitType.IMPORT;
        }
        return RateLimitType.GENERAL;
    }

    private String extractClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "user:" + auth.getName();
        }

        // Unauthenticated client IP
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String clientIp = forwarded.split(",")[0].trim();
            if (!clientIp.isBlank()) {
                return "ip:" + clientIp;
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
