# FreightFox Transporter Assignment on Lanes Platform

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon%20Cloud-4169E1?logo=postgresql&logoColor=white)](https://neon.tech/)
[![Redis](https://img.shields.io/badge/Redis-7%20Distributed-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Swagger](https://img.shields.io/badge/OpenAPI-Swagger%20UI-85EA2D?logo=swagger&logoColor=black)](https://freightfox-transporter-assignment-production.up.railway.app/swagger-ui/index.html)
[![Railway](https://img.shields.io/badge/Deployed-Railway-0B0D0E?logo=railway&logoColor=white)](https://freightfox-transporter-assignment-production.up.railway.app)
[![Tests Passing](https://img.shields.io/badge/Tests-60%20Passed-success?logo=junit5&logoColor=white)](#automated-testing--regression-coverage)

A production-grade, secure, cloud-native REST backend platform built for the **FreightFox Transporter Assignment on Lanes** technical assessment. The platform solves the multi-lane freight procurement problem by optimizing transporter selection and lane assignment under total spend minimization, transporter fleet limits, and complete coverage constraints.

> **Note:** This repository is an **API-only backend service**. There is no graphical user interface. All interactions occur through REST endpoints, the live OpenAPI / Swagger UI interface, or HTTP clients such as cURL and Postman.

---

## Live Demo & API Documentation

| Resource | Direct Link | Description |
|---|---|---|
| **Live Swagger UI** | [https://freightfox-transporter-assignment-production.up.railway.app/swagger-ui/index.html](https://freightfox-transporter-assignment-production.up.railway.app/swagger-ui/index.html) | Interactive OpenAPI 3 interface with JWT Bearer authorization |
| **Production Health Check** | [https://freightfox-transporter-assignment-production.up.railway.app/api/v1/health](https://freightfox-transporter-assignment-production.up.railway.app/api/v1/health) | Real-time backend status, database, and Redis health probes |
| **Production API Base URL** | [https://freightfox-transporter-assignment-production.up.railway.app](https://freightfox-transporter-assignment-production.up.railway.app/) | Root URL for all production REST API requests |
| **GitHub Repository** | [https://github.com/harshsingh910/freightfox-transporter-assignment](https://github.com/harshsingh910/freightfox-transporter-assignment) | Canonical source repository with full commit and audit history |

---

## Table of Contents

1. [Overview & Problem Statement](#overview--problem-statement)
2. [Key Features](#key-features)
3. [Architecture & Request Flow](#architecture--request-flow)
4. [Technology Stack](#technology-stack)
5. [Core Optimization Engine](#core-optimization-engine)
6. [CSV Dataset Format & Validation Rules](#csv-dataset-format--validation-rules)
7. [API Reference & Endpoint Table](#api-reference--endpoint-table)
8. [Example cURL API Requests](#example-curl-api-requests)
9. [Authentication & Authorization](#authentication--authorization)
10. [Database Design & Schema](#database-design--schema)
11. [Redis Caching & Distributed Rate Limiting](#redis-caching--distributed-rate-limiting)
12. [Local Setup & Installation](#local-setup--installation)
13. [Environment Variables & Secure Configuration](#environment-variables--secure-configuration)
14. [Automated Testing & Regression Coverage](#automated-testing--regression-coverage)
15. [Docker & Containerization](#docker--containerization)
16. [Production Deployment on Railway](#production-deployment-on-railway)
17. [Example End-to-End Workflow](#example-end-to-end-workflow)
18. [Troubleshooting Guide](#troubleshooting-guide)
19. [Security Hardening & Production Safeguards](#security-hardening--production-safeguards)
20. [Limitations & Scaling Considerations](#limitations--scaling-considerations)
21. [Future Planned Improvements](#future-planned-improvements)

---

## Overview & Problem Statement

In freight logistics networks across India, enterprise shippers contract freight across dozens of origin-destination trade lanes (e.g., *Mumbai &rarr; Delhi*, *Delhi &rarr; Bangalore*, *Chennai &rarr; Kolkata*). Multiple transport service providers submit cost quotes for lanes they can service.

Shippers face a dual operational challenge:
1. **Consolidation Ceiling ($K$):** Managing too many carriers creates operational overhead. Logistics managers impose a ceiling on the number of distinct transporters permitted in the final network ($K$).
2. **Supplier Diversification vs. Cost:** Sole-sourcing creates supply-chain vulnerability, while unrestricted carrier sprawl increases tracking costs. The assignment engine must select a subset of at most $K$ carriers that can service **every single lane** at the lowest total freight spend.

### The Optimization Objective

The platform implements the assignment's official multi-objective strategy, identified as:
$$\text{LEXICOGRAPHIC\_MAX\_TRANSPORTERS\_MIN\_COST}$$

The optimization engine evaluates candidate solutions using a strict lexicographical hierarchy:
1. **Priority 1 (Supplier Diversification):** Maximize the number of distinct transporters utilized ($M_{\text{used}} \le K$). If $K = 3$ and feasible combinations exist that utilize 3 transporters, solutions using only 1 or 2 transporters are eliminated to maintain vendor resilience.
2. **Priority 2 (Cost Minimization):** Among all feasible subsets achieving that maximal transporter count, minimize the aggregate procurement spend:
   $$\min \sum_{i=1}^N \min_{j \in S} C_{i, j}$$
3. **Priority 3 (Deterministic Tie-Breaking):** If multiple combinations achieve both identical carrier count and identical minimum cost, break ties deterministically using the natural lexicographical order of transporter identifiers.

The engine also supports a pure cost minimization objective (`MIN_COST`), selectable per request.

---

## Key Features

- **Exact Combinatorial Optimization:** Enumerates and evaluates carrier subsets to guarantee a mathematically exact global optimum without heuristic approximation error.
- **Dual Ingestion Pipelines:** Supports both transactional RFC 4180 CSV file uploads (`POST /api/v1/import/csv`) and programmatic JSON input requests (`POST /api/v1/transporters/input`).
- **Enterprise Spring Security:** Stateless JWT authentication (HMAC-SHA 256-bit+), BCrypt password hashing (cost factor 12), strict 12-character password complexity policies, and deny-by-default role-based authorization (`ROLE_USER`, `ROLE_ADMIN`).
- **Fail-Fast Production Secret Guards:** Reject missing secrets, public repository defaults, and weak keys on startup in the `prod` profile.
- **Distributed Redis Rate Limiting:** Enforces token-bucket rate limits powered by Bucket4j and Lettuce across login, registration, CSV import, and optimization endpoints, with automatic in-memory fallback.
- **Distributed Redis Caching:** Caches lane and transporter queries with multi-entity eviction (`@CacheEvict`) on input ingestion.
- **Cloud Database Persistence:** PostgreSQL 16 persistence (Neon cloud in production) managed through Flyway V1 schema migrations with foreign keys, checks, and compound indexes.
- **Robust Cloud DataSource Parsing:** Transparently parses and normalizes Railway `DATABASE_URL` (`postgresql://` or `postgres://`), injecting required SSL parameters for cloud pooling.
- **Paginated Assignment History:** Safe DTO-based pagination (`GET /api/v1/assignments`) completely decoupled from Hibernate sessions, preventing `LazyInitializationException`.
- **Comprehensive Test Suite:** 60 automated unit, integration, and security regression tests verified with Java 21 and Maven.

---

## Architecture & Request Flow

```mermaid
graph TD
    Client["Client / cURL / Swagger UI / Postman"]

    subgraph "Spring Boot 3.3.4 Backend (Port 8080)"
        Security["JwtAuthenticationFilter<br/>(Bearer Token Validation & MDC Trace ID)"]
        RateLimiter["RateLimitFilter<br/>(Bucket4j + Redis Token Bucket)"]

        subgraph "REST Controllers"
            HealthCtrl["HealthController<br/>/api/v1/health"]
            AuthCtrl["AuthController<br/>/api/v1/auth/*"]
            CsvCtrl["CsvImportController<br/>/api/v1/import/csv"]
            LaneCtrl["LaneController<br/>/api/v1/lanes"]
            TransCtrl["TransporterController<br/>/api/v1/transporters"]
            AssignCtrl["AssignmentController<br/>/api/v1/transporters/*<br/>/api/v1/assignments/*"]
        end

        subgraph "Application Services"
            AuthService["AuthService<br/>(BCrypt & Account Preservation)"]
            CsvParser["CsvParserService<br/>(Apache Commons CSV)"]
            CsvImport["CsvImportService<br/>(Transactional Upserts)"]
            AssignService["AssignmentService<br/>(Problem Orchestration)"]
        end

        subgraph "Decoupled Optimization Engine"
            Optimizer["ExactSubsetsTransporterOptimizer<br/>• Combinatorial Subset Enumeration<br/>• Complete Coverage Verification<br/>• Lexicographic Tie-Breaking"]
        end

        GlobalEx["GlobalExceptionHandler<br/>(Structured Error Responses + Retry-After)"]
    end

    subgraph "External Cloud Infrastructure"
        RedisServer[("Redis 7 (Lettuce)<br/>• Distributed Rate Limiting<br/>• Query Cache (lanes, transporters)")]
        PostgresDB[("PostgreSQL 16 (Neon)<br/>• Flyway Schema Migrations<br/>• Lanes, Transporters, Quotes<br/>• Historical Assignment Runs")]
    end

    Client -->|HTTP Request| Security
    Security --> RateLimiter
    RateLimiter --> HealthCtrl
    RateLimiter --> AuthCtrl
    RateLimiter --> CsvCtrl
    RateLimiter --> LaneCtrl
    RateLimiter --> TransCtrl
    RateLimiter --> AssignCtrl

    CsvCtrl --> CsvImport
    CsvImport --> CsvParser
    AssignCtrl --> AssignService
    AssignService --> Optimizer

    AuthService -.-> PostgresDB
    CsvImport -.-> PostgresDB
    AssignService -.-> PostgresDB
    AssignService -.-> RedisServer
    RateLimiter -.-> RedisServer
    HealthCtrl -.-> PostgresDB
    HealthCtrl -.-> RedisServer
```

---

## Technology Stack

| Layer | Component | Version | Description |
|---|---|---|---|
| **Language** | Java OpenJDK | `21.0.12 LTS` | Modern Java runtime utilizing records, pattern matching, and sealed types |
| **Framework** | Spring Boot | `3.3.4` | Enterprise application foundation with Spring MVC, Actuator, and Validation |
| **Security** | Spring Security + JJWT | `6.3.3` / `0.12.6` | Stateless JWT Bearer token authentication, BCrypt hashing, and method security |
| **Persistence** | Spring Data JPA / Hibernate | `6.5.3` | ORM entities, Spring Data repositories, HikariCP connection pooling |
| **Primary Database** | PostgreSQL | `16` (Neon Cloud) | Production relational store with ACID guarantees, foreign keys, and indexes |
| **Test Database** | H2 Database Engine | `2.2.224` | In-memory database with PostgreSQL compatibility mode for isolated test suites |
| **Migrations** | Flyway | `10.15.2` | Versioned, repeatable SQL schema migrations (`V1__init_schema.sql`) |
| **Caching & Redis** | Spring Data Redis + Lettuce | `3.3.4` / `6.3.2` | Distributed caching with automatic fallback to `ConcurrentMapCacheManager` |
| **Rate Limiting** | Bucket4j | `8.10.1` | Distributed token-bucket algorithm using `bucket4j-redis` Lettuce CAS proxy |
| **CSV Processing** | Apache Commons CSV | `1.11.0` | RFC 4180-compliant CSV parser handling whitespace, missing values, and quotes |
| **API Docs** | Springdoc OpenAPI Starter | `2.6.0` | OpenAPI 3.0 specification generator and interactive Swagger UI interface |
| **Build & Packaging**| Apache Maven / Wrapper | `3.9.x` | Dependency management and multi-stage container build tool |
| **Containerization**| Docker | Alpine | Multi-stage build producing an unprivileged runtime container (`appuser`) |
| **Deployment** | Railway | Cloud | Automated container deployment with persistent Redis and Neon PostgreSQL |

---

## Core Optimization Engine

### Formal Problem Formulation

Given:
- A set of $N$ trade lanes: $L = \{l_1, l_2, \dots, l_N\}$
- A set of $M$ transporters: $T = \{t_1, t_2, \dots, t_M\}$
- A cost matrix $C_{i, j} > 0$ representing the quote submitted by transporter $t_j$ on lane $l_i$
- A transporter ceiling constraint $K \ge 1$

**Subject to:**
1. **Complete Coverage:** Every lane $l_i$ must be assigned to exactly one transporter $t_j \in S$.
2. **Fleet Constraint:** The selected subset $S \subseteq T$ must satisfy $|S| \le \min(K, M)$.
3. **Quote Validity:** An assignment is feasible only if $C_{i, j}$ exists (i.e., transporter $t_j$ actually submitted a quote for lane $l_i$).
4. **Lane Allocation:** For any chosen subset $S$, each lane $l_i$ is assigned to the transporter in $S$ offering the lowest quote:
   $$j^*(i) = \arg\min_{j \in S} C_{i, j}$$

### Algorithm Execution & Tie-Breaking

The `ExactSubsetsTransporterOptimizer` operates as follows:
1. **Quote Matrix Indexing:** Loads all quotes into a two-level hash map (`Map<LaneId, Map<TransporterId, Quote>>`) enabling $O(1)$ quote lookups.
2. **Pre-check Feasibility:** Verifies that every lane has at least one quote from some transporter in $T$. If any lane has zero quotes, optimization immediately returns infeasible.
3. **Combinatorial Subset Generation:** Generates all combinations $\binom{M}{k}$ for subset sizes $k = 1, 2, \dots, \min(K, M)$.
4. **Subset Evaluation:** For each candidate subset $S$:
   - Verifies whether every lane can be serviced by at least one member of $S$.
   - Computes total cost $\sum_{i=1}^N \min_{j \in S} C_{i, j}$.
   - Resolves intra-lane quote ties deterministically by selecting the lexicographically smaller transporter identifier.
5. **Multi-Objective Selection:**
   - Under `LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST`:
     $$\text{Candidate } A \succ B \iff |A_{\text{used}}| > |B_{\text{used}}| \quad \lor \quad (|A_{\text{used}}| = |B_{\text{used}}| \land \text{Cost}(A) < \text{Cost}(B))$$
   - Under `MIN_COST`:
     $$\text{Candidate } A \succ B \iff \text{Cost}(A) < \text{Cost}(B) \quad \lor \quad (\text{Cost}(A) = \text{Cost}(B) \land |A_{\text{used}}| > |B_{\text{used}}|)$$
6. **Deterministic Tie-Breaker:** When costs and carrier counts are equal, compares sorted carrier ID lists lexicographically.

### Complexity & Scalability Safeguards

- **Combinatorial Combinations:** The total number of subsets evaluated is $\sum_{k=1}^{\min(K, M)} \binom{M}{k}$.
- **Combinatorial Cap:** The optimizer calculates the total combinations beforehand. If this exceeds `maxCombinatorialSubsets` (default: **50,000**), the optimizer aborts before execution to prevent CPU starvation.
- **Execution Deadline:** If evaluation time exceeds `maxRuntimeMillis` (default: **30,000 ms**), a runtime exception is thrown.
- **Infeasibility Response:** If no combination of up to $K$ carriers can cover all lanes, the API returns **HTTP 422 Unprocessable Entity** with an informative `INFEASIBLE_ASSIGNMENT` error payload.

---

## CSV Dataset Format & Validation Rules

The platform accepts CSV uploads via `POST /api/v1/import/csv`. The expected format conforms to the standard procurement matrix provided in `transporter_assignment_data.csv`.

### CSV Structure

- **Row 1 (Header):** First column must identify trade lanes (`Trade Lane` or similar). Subsequent columns represent individual transporter names/identifiers (`T1`, `T2`, etc.).
- **Rows 2..N (Data Rows):** First column contains the trade lane identifier or origin-destination pair. Subsequent columns contain the positive numeric quote amounts from each transporter.

### Verified Sample Dataset

```csv
Trade Lane,T1,T2,T3,T4,T5,T6,T7
Lane 1,20835,48844,39020,14400,11601,35095,26070
Lane 2,10512,31326,20648,44514,19760,12494,41098
Lane 3,22105,18640,31438,14316,40870,17808,20932
Lane 4,42481,45828,36447,10678,20635,36210,16897
Lane 5,19862,18297,12789,13032,26421,39444,27938
Lane 6,13567,45810,49985,46024,28809,29948,43517
Lane 7,10015,49573,35285,42342,27815,17320,25881
Lane 8,17886,45122,10281,35742,17024,10492,46136
Lane 9,43996,31856,40092,48921,44691,37864,31286
```

### Parsing & Validation Rules

1. **Header Validation:** Requires at least 2 columns. Detects and rejects duplicate or empty transporter column names.
2. **Lane Identification:** Automatically extracts `origin` and `destination` if the lane name contains standard separators (`Mumbai -> Delhi` or `Mumbai - Delhi`). If unnamed, sets origin and destination to the lane identifier.
3. **Numeric Quotes:** Every quote must be a positive decimal number (`quote > 0`). Non-numeric values (e.g., `"N/A"`, `"-500"`) trigger validation errors.
4. **Strict vs. Non-Strict Mode:**
   - `strictMode=true` (Default): Any missing quote cell fails the entire upload with an explicit line-by-line validation report.
   - `strictMode=false`: Empty quote cells are treated as non-participating transporters on that specific lane (sparse quote matrix) and recorded as warnings.
5. **Transactional Upsert:** CSV import runs in a single `@Transactional` boundary. If any validation rule fails, **zero records are persisted**. If valid, existing lanes and transporters are updated idempotently by external ID, and quotes are refreshed.

---

## API Reference & Endpoint Table

All application endpoints are versioned under `/api/v1`.

| Method | Endpoint | Description | Auth / Role Required | Rate Limit Tier |
|---|---|---|---|---|
| `GET` | `/api/v1/health` | Service health, database, and Redis connectivity status | Public | None |
| `POST` | `/api/v1/auth/register` | Register a new user account with password policy checks | Public | 5 / hour |
| `POST` | `/api/v1/auth/login` | Authenticate credentials and receive a signed JWT token | Public | 5 / minute |
| `GET` | `/api/v1/auth/me` | Retrieve profile of the currently authenticated user | Bearer JWT (`USER`, `ADMIN`) | 120 / minute |
| `POST` | `/api/v1/import/csv` | Upload and persist trade lanes, transporters, and quotes | Bearer JWT (`ADMIN`) | 5 / minute |
| `POST` | `/api/v1/transporters/input` | Ingest lanes and quotes via structured JSON payload | Bearer JWT (`ADMIN`) | 120 / minute |
| `POST` | `/api/v1/transporters/assignment` | Execute the transporter assignment optimization run | Bearer JWT (`USER`, `ADMIN`) | 20 / minute |
| `GET` | `/api/v1/assignments` | Retrieve paginated list of historical optimization runs | Bearer JWT (`USER`, `ADMIN`) | 120 / minute |
| `GET` | `/api/v1/assignments/{id}` | Retrieve details and lane allocations of a specific run | Bearer JWT (`USER`, `ADMIN`) | 120 / minute |
| `GET` | `/api/v1/lanes` | List trade lanes with pagination and sorting | Bearer JWT (`USER`, `ADMIN`) | 120 / minute |
| `GET` | `/api/v1/lanes/{id}` | Retrieve specific lane details by internal ID | Bearer JWT (`USER`, `ADMIN`) | 120 / minute |
| `GET` | `/api/v1/transporters` | List transporters with pagination and sorting | Bearer JWT (`USER`, `ADMIN`) | 120 / minute |
| `GET` | `/api/v1/transporters/{id}` | Retrieve specific transporter details by internal ID | Bearer JWT (`USER`, `ADMIN`) | 120 / minute |
| `GET` | `/swagger-ui/index.html` | Interactive Swagger UI API documentation | Public | None |
| `GET` | `/v3/api-docs` | OpenAPI 3.0 JSON specification | Public | None |
| `GET` | `/actuator/health` | Spring Boot Actuator liveness and readiness probe | Public | None |

---

## Example cURL API Requests

Replace `<API_BASE_URL>` with `http://localhost:8080` for local testing or `https://freightfox-transporter-assignment-production.up.railway.app` for production.

### 1. Health Check (Public)

```bash
curl -s -X GET "<API_BASE_URL>/api/v1/health" | jq .
```

**Response (HTTP 200):**
```json
{
  "status": "UP",
  "version": "1.0.0",
  "uptimeMs": 42560,
  "timestamp": "2026-10-13T10:15:35.123Z",
  "dependencies": {
    "database": "UP",
    "redis": "UP"
  }
}
```

### 2. User Registration (Public)

```bash
curl -s -X POST "<API_BASE_URL>/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "logistics_user",
    "email": "user@freightfox.com",
    "password": "SecurePassword2026!"
  }' | jq .
```

### 3. User Login (Public)

```bash
curl -s -X POST "<API_BASE_URL>/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "logistics_user",
    "password": "SecurePassword2026!"
  }' | jq .
```

**Response (HTTP 200):**
```json
{
  "token": "<JWT_TOKEN>",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": {
    "id": 2,
    "username": "logistics_user",
    "email": "user@freightfox.com",
    "role": "ROLE_USER"
  }
}
```

### 4. Get Current User Profile (Authenticated)

```bash
curl -s -X GET "<API_BASE_URL>/api/v1/auth/me" \
  -H "Authorization: Bearer <JWT_TOKEN>" | jq .
```

### 5. CSV Dataset Import (Administrator Only)

```bash
curl -s -X POST "<API_BASE_URL>/api/v1/import/csv?strictMode=true" \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -F "file=@transporter_assignment_data.csv" | jq .
```

**Response (HTTP 200):**
```json
{
  "status": "SUCCESS",
  "lanesCount": 9,
  "transportersCount": 7,
  "quotesCount": 63,
  "errors": [],
  "warnings": [],
  "message": "CSV imported successfully: 9 lanes, 7 transporters, 63 quotes persisted."
}
```

### 6. Query Lanes & Transporters (Authenticated)

```bash
# List lanes (paginated)
curl -s -X GET "<API_BASE_URL>/api/v1/lanes?page=0&size=10" \
  -H "Authorization: Bearer <JWT_TOKEN>" | jq .

# List transporters (paginated)
curl -s -X GET "<API_BASE_URL>/api/v1/transporters?page=0&size=10" \
  -H "Authorization: Bearer <JWT_TOKEN>" | jq .
```

### 7. Run Transporter Assignment Optimization (Authenticated)

```bash
curl -s -X POST "<API_BASE_URL>/api/v1/transporters/assignment" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "maxTransporters": 3,
    "objective": "LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST"
  }' | jq .
```

**Response (HTTP 200):**
```json
{
  "status": "success",
  "assignmentRunId": 1,
  "totalCost": 134876.00,
  "selectedTransporters": ["T1", "T4", "T5"],
  "selectedTransporterCount": 3,
  "lanesCovered": 9,
  "optimizationObjective": "LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST",
  "assignments": [
    {
      "laneId": "Lane 1",
      "transporterId": "T5",
      "quoteAmount": 11601.00,
      "origin": "Lane 1",
      "destination": "Lane 1",
      "transporterName": "T5"
    },
    {
      "laneId": "Lane 2",
      "transporterId": "T1",
      "quoteAmount": 10512.00,
      "origin": "Lane 2",
      "destination": "Lane 2",
      "transporterName": "T1"
    },
    {
      "laneId": "Lane 3",
      "transporterId": "T4",
      "quoteAmount": 14316.00,
      "origin": "Lane 3",
      "destination": "Lane 3",
      "transporterName": "T4"
    },
    {
      "laneId": "Lane 4",
      "transporterId": "T4",
      "quoteAmount": 10678.00,
      "origin": "Lane 4",
      "destination": "Lane 4",
      "transporterName": "T4"
    },
    {
      "laneId": "Lane 5",
      "transporterId": "T4",
      "quoteAmount": 13032.00,
      "origin": "Lane 5",
      "destination": "Lane 5",
      "transporterName": "T4"
    },
    {
      "laneId": "Lane 6",
      "transporterId": "T1",
      "quoteAmount": 13567.00,
      "origin": "Lane 6",
      "destination": "Lane 6",
      "transporterName": "T1"
    },
    {
      "laneId": "Lane 7",
      "transporterId": "T1",
      "quoteAmount": 10015.00,
      "origin": "Lane 7",
      "destination": "Lane 7",
      "transporterName": "T1"
    },
    {
      "laneId": "Lane 8",
      "transporterId": "T5",
      "quoteAmount": 17024.00,
      "origin": "Lane 8",
      "destination": "Lane 8",
      "transporterName": "T5"
    },
    {
      "laneId": "Lane 9",
      "transporterId": "T5",
      "quoteAmount": 44691.00,
      "origin": "Lane 9",
      "destination": "Lane 9",
      "transporterName": "T5"
    }
  ]
}
```

### 8. List Historical Assignment Runs (Authenticated)

```bash
curl -s -X GET "<API_BASE_URL>/api/v1/assignments?page=0&size=10" \
  -H "Authorization: Bearer <JWT_TOKEN>" | jq .
```

---

## Authentication & Authorization

### Stateless JWT Bearer Flow

1. **Authentication:** Clients provide username and password to `/api/v1/auth/login`.
2. **Token Generation:** Upon successful authentication, the server signs an HMAC-SHA token containing `sub` (username), `roles` (e.g., `["ROLE_USER"]`), issue timestamp, and expiration timestamp.
3. **Token Verification:** On subsequent requests, `JwtAuthenticationFilter` intercepts the `Authorization: Bearer <token>` header, verifies the signature against the configured `JWT_SECRET`, loads the `UserDetails`, and injects an authenticated token into Spring's `SecurityContext`.

### Password Policy

The `PasswordValidator` enforces the following policy on user registration:
- Length between 12 and 128 characters
- At least one uppercase letter (`A-Z`)
- At least one lowercase letter (`a-z`)
- At least one numeric digit (`0-9`)
- At least one special symbol (`!@#$%^&*...`)
- Denylist filter rejecting commonly guessed passwords

### Bootstrap Administrator Provisioning

The application can initialize an initial administrator account on startup via `AuthService`:
- Controlled by `BOOTSTRAP_ADMIN_ENABLED` (default: `true`).
- **Account Preservation Guarantee:** On startup, `AuthService` executes `userRepository.existsByUsername(bootstrapAdminUsername)`. If an account with that username already exists, **no changes are made**. The existing account's credentials, password hash, and assigned privileges are strictly preserved.
- **Production Guard:** If creating a new bootstrap admin account in the `prod` profile, `BOOTSTRAP_ADMIN_PASSWORD` must be provided explicitly and cannot match known insecure fallback defaults.

---

## Database Design & Schema

Database schema changes are versioned and applied automatically via Flyway (`db/migration/V1__init_schema.sql`).

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar role
        boolean enabled
        timestamp created_at
        timestamp updated_at
    }

    LANES {
        bigint id PK
        varchar external_lane_id UK
        varchar origin
        varchar destination
        timestamp created_at
        timestamp updated_at
    }

    TRANSPORTERS {
        bigint id PK
        varchar external_transporter_id UK
        varchar name
        timestamp created_at
        timestamp updated_at
    }

    TRANSPORTER_QUOTES {
        bigint id PK
        bigint lane_id FK
        bigint transporter_id FK
        numeric quote_amount
        timestamp created_at
        timestamp updated_at
    }

    ASSIGNMENT_RUNS {
        bigint id PK
        int requested_max_transporters
        int selected_transporter_count
        numeric total_cost
        varchar status
        varchar optimization_objective
        int lanes_covered
        varchar created_by
        timestamp created_at
    }

    LANE_ASSIGNMENTS {
        bigint id PK
        bigint assignment_run_id FK
        bigint lane_id FK
        bigint transporter_id FK
        numeric quote_amount
    }

    LANES ||--o{ TRANSPORTER_QUOTES : "has quotes"
    TRANSPORTERS ||--o{ TRANSPORTER_QUOTES : "submits"
    ASSIGNMENT_RUNS ||--o{ LANE_ASSIGNMENTS : "contains"
    LANES ||--o{ LANE_ASSIGNMENTS : "assigned in"
    TRANSPORTERS ||--o{ LANE_ASSIGNMENTS : "fulfills"
```

### Table Summary & Constraints

- **`users`:** Stores system credentials. Unique constraints on `username` and `email`.
- **`lanes`:** Normalized trade lanes. Unique constraint on `external_lane_id`. Indexed for search.
- **`transporters`:** Registered carriers. Unique constraint on `external_transporter_id`.
- **`transporter_quotes`:** Quote matrix. Foreign keys to `lanes` and `transporters` with `ON DELETE CASCADE`. Unique constraint on `(lane_id, transporter_id)` and check constraint `quote_amount > 0`.
- **`assignment_runs`:** Optimization history logs. Stores parameters, selected count, total cost, and creator. Indexed on `created_at DESC`.
- **`lane_assignments`:** Individual lane allocations belonging to a run. Foreign keys to `assignment_runs` (`ON DELETE CASCADE`), `lanes` (`ON DELETE RESTRICT`), and `transporters` (`ON DELETE RESTRICT`).

---

## Redis Caching & Distributed Rate Limiting

### Distributed Rate Limiting (Bucket4j + Lettuce)

All inbound API traffic passes through `RateLimitFilter`:
- **Client Key:** Authenticated users are tracked by `user:<username>`. Unauthenticated clients are tracked by IP extracted from `X-Forwarded-For` or the socket remote address (`ip:<address>`).
- **Distributed State:** Buckets are stored in Redis using Lettuce compare-and-swap (`LettuceBasedProxyManager`), ensuring quotas are maintained across horizontally scaled backend instances.
- **Quota Tiers:**
  - Login: 5 requests / 60 seconds
  - Registration: 5 requests / 3600 seconds
  - Optimization: 20 requests / 60 seconds
  - CSV Import: 5 requests / 60 seconds
  - General queries: 120 requests / 60 seconds
- **Fallback Protection:** If Redis is unreachable, `RateLimitService` automatically falls back to thread-safe local in-memory buckets (`ConcurrentMapCacheManager`), allowing the service to remain functional without dropping legitimate traffic.

### Caching Strategy

- Caches trade lanes (`lanes`), transporters (`transporters`), and historical assignments (`assignments`) with a 10-minute default time-to-live (`TTL`).
- When new data is ingested via CSV upload (`/api/v1/import/csv`) or JSON input (`/api/v1/transporters/input`), `@CacheEvict(value = {"lanes", "transporters", "assignments"}, allEntries = true)` purges cached query results atomically.

---

## Local Setup & Installation

### Prerequisites

- **Java Development Kit:** Java 21 LTS (Oracle, Eclipse Temurin, or OpenJDK)
- **Maven:** 3.9+ (or use the included `./mvnw` wrapper)
- **Git**
- **Docker & Docker Compose** (Optional: for local PostgreSQL and Redis containers)

### Step-by-Step Installation

#### 1. Clone the Repository

```bash
git clone https://github.com/harshsingh910/freightfox-transporter-assignment.git
cd freightfox-transporter-assignment
```

#### 2. Verify Java 21

```bash
java -version
# Ensure output indicates OpenJDK 21 or Java 21
```

If your default system Java points to an older version (e.g., Java 17 on macOS), set `JAVA_HOME` explicitly:
```bash
# macOS with Homebrew:
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
```

#### 3. Run Automated Tests

```bash
./mvnw clean test
```
All 60 tests should pass cleanly using the default in-memory H2 database.

#### 4. Package the Application

```bash
./mvnw clean package
```
Generates the executable JAR: `target/transporter-assignment-platform-1.0.0.jar`.

#### 5. Launch the Application Locally

```bash
./mvnw spring-boot:run
```

By default, the application starts with the `local` profile on port `8080`, using an in-memory H2 database with PostgreSQL compatibility mode and in-memory rate-limiting fallback.

#### 6. Access Local Swagger UI

Open your browser to:
[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## Environment Variables & Secure Configuration

The application is configured via `src/main/resources/application.yml` and `src/main/resources/application-prod.yml`. A safe template is provided in `.env.example`.

### Configuration Variables Reference

| Variable | Description | Default (Local) | Production Requirement |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `local` | **Required (`prod`)** |
| `PORT` | HTTP server port | `8080` | Assigned automatically by Railway |
| `DATABASE_URL` | Cloud PostgreSQL URI (Railway/Neon) | `jdbc:h2:mem:freightfox;...` | **Required in Production** |
| `JWT_SECRET` | 256-bit+ HMAC signing secret | Safe local testing key | **Required in Production** (min 32 bytes) |
| `JWT_ISSUER` | JWT token issuer claim | `freightfox-platform` | Optional |
| `JWT_ACCESS_TOKEN_TTL` | JWT validity duration (seconds) | `86400` (24h) | Optional |
| `BOOTSTRAP_ADMIN_ENABLED`| Initialize admin on startup | `true` | Optional (`true` / `false`) |
| `BOOTSTRAP_ADMIN_USERNAME`| Username of bootstrap admin | `admin` | Optional |
| `BOOTSTRAP_ADMIN_EMAIL` | Email of bootstrap admin | `admin@freightfox.com` | Optional |
| `BOOTSTRAP_ADMIN_PASSWORD`| Password of bootstrap admin | Tested local default | **Required if admin does not exist** |
| `REDIS_HOST` | Redis server hostname | `localhost` | **Required in Production** (Railway service) |
| `REDIS_PORT` | Redis server port | `6379` | **Required in Production** |
| `REDIS_PASSWORD` | Redis server authentication password | None | Set if Redis requires auth |
| `RATE_LIMIT_ENABLED` | Enable rate limiting filter | `true` | Recommended `true` |
| `RATE_LIMIT_FALLBACK_IN_MEMORY` | Fall back to RAM if Redis is down | `true` (local) / `false` (prod) | Optional |
| `OPTIMIZER_OBJECTIVE` | Default optimization strategy | `LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST` | Optional |
| `CORS_ALLOWED_ORIGINS` | Permitted browser origins | `*` | Configure domain in production |

### Production Security Rules

1. **Mandatory Explicit Secrets:** When running with `SPRING_PROFILES_ACTIVE=prod`, `JWT_SECRET` must be set explicitly. The application will fail startup with an informative error if it is missing, shorter than 32 bytes (256 bits), or matches the public default key.
2. **Generating a Secure Secret:**
   ```bash
   openssl rand -hex 32
   ```
3. **Rotating the JWT Secret:** Updating `JWT_SECRET` immediately invalidates previously issued tokens. It does **not** alter or damage database records, trade lanes, quotes, or historical assignment runs.
4. **Bootstrap Admin Idempotency:** The bootstrap admin routine checks if the admin user exists in the database. If present, it leaves existing passwords and roles intact.

---

## Automated Testing & Regression Coverage

The repository maintains an automated test suite verifying every component from algorithms to security filters.

### Running Test Suites

```bash
# Run unit, integration, and security tests
./mvnw clean test

# Run build verification and generate executable JAR
./mvnw clean package
```

### Verified Test Scope (60 Tests Total)

- **Exact Optimization Tests (`ExactSubsetsTransporterOptimizerTest` - 18 tests):**
  - Verifies optimality against the official 9-lane, 7-transporter dataset.
  - Verifies behavior for $K = 1, 2, 3, 4, 7$.
  - Tests infeasibility detection when $K$ is too small for full lane coverage.
  - Tests handling of missing quotes and empty inputs.
  - Validates combinatorial threshold enforcement ($> 50,000$ combinations).
  - Validates lexicographical tie-breaking rules.
- **Security & Secret Validation (`SecretConfigurationRegressionTest` - 11 tests):**
  - Rejects missing, blank, weak, and public fallback JWT secrets in production.
  - Rejects public bootstrap admin passwords during account creation in production.
  - Verifies existing admin account preservation without password resets.
  - Verifies non-production local profiles continue to permit safe defaults.
- **Authentication & Password Tests (`AuthControllerTest`, `PasswordValidatorTest` - 13 tests):**
  - Verifies user registration, login, token issuance, and password complexity requirements.
  - Rejects passwords lacking uppercase, lowercase, numbers, or symbols.
- **CSV Parser & Import Integration (`CsvParserServiceTest`, `CsvImportIntegrationTest` - 7 tests):**
  - Validates RFC 4180 parsing, header validation, duplicate lane detection, non-numeric quote handling, and transactional database persistence.
- **Distributed Rate Limiting (`RateLimitIntegrationTest` - 1 test):**
  - Verifies Bucket4j token consumption and HTTP 429 response formatting with `Retry-After` headers.
- **Infrastructure & Health Probes (`HealthControllerTest`, `DataSourceConfigTest`, `RedisConfigTest` - 10 tests):**
  - Verifies dynamic Railway URL transformation, database health probes, and Redis connection diagnostics.

---

## Docker & Containerization

A multi-stage `Dockerfile` packages the application into a secure, minimal container running under an unprivileged user.

### Building the Docker Image

```bash
docker build -t freightfox-platform:latest .
```

### Running the Container

```bash
docker run -d -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL="postgresql://user:pass@host:5432/dbname?sslmode=require" \
  -e JWT_SECRET="your_secure_256_bit_jwt_secret_hex_here_min_32_bytes" \
  --name freightfox-backend \
  freightfox-platform:latest
```

### Multi-Container Local Stack (`docker-compose.yml`)

To run the application alongside local PostgreSQL and Redis containers:

```bash
docker compose up -d
```
This initializes:
- `freightfox-postgres` on port `5432`
- `freightfox-redis` on port `6379`
- `freightfox-backend` on port `8080`

---

## Production Deployment on Railway

The live deployment on Railway uses the following architecture:
1. **Application Service:** Runs the Spring Boot Docker container built directly from the GitHub repository (`Dockerfile` via `railway.json`).
2. **Database:** Hosted on Neon PostgreSQL with connection pooling and SSL enabled.
3. **Cache & Rate Limiting:** Redis service hosted on Railway, linked to the backend via `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD`.

### Railway Configuration (`railway.json`)

```json
{
  "$schema": "https://railway.app/railway.schema.json",
  "build": {
    "builder": "DOCKERFILE",
    "dockerfilePath": "Dockerfile"
  },
  "deploy": {
    "startCommand": "java -Dserver.port=${PORT:-8080} -XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -jar app.jar",
    "healthcheckPath": "/api/v1/health",
    "healthcheckTimeout": 120,
    "restartPolicyType": "ON_FAILURE",
    "restartPolicyMaxRetries": 5
  }
}
```

### Deployment Verification

Once deployed on Railway, verify application health via:
```bash
curl -s https://freightfox-transporter-assignment-production.up.railway.app/api/v1/health | jq .
```
Verify that both `"database": "UP"` and `"redis": "UP"` are reported.

---

## Example End-to-End Workflow

The following complete workflow illustrates how to operate the platform from start to finish:

1. **Verify Backend Health:**
   ```bash
   curl -s "https://freightfox-transporter-assignment-production.up.railway.app/api/v1/health" | jq .
   ```
2. **Log in as Administrator:**
   ```bash
   ADMIN_TOKEN=$(curl -s -X POST "https://freightfox-transporter-assignment-production.up.railway.app/api/v1/auth/login" \
     -H "Content-Type: application/json" \
     -d '{"username":"<ADMIN_USERNAME>","password":"<ADMIN_PASSWORD>"}' | jq -r .token)
   ```
3. **Upload the Procurement Dataset:**
   ```bash
   curl -s -X POST "https://freightfox-transporter-assignment-production.up.railway.app/api/v1/import/csv?strictMode=true" \
     -H "Authorization: Bearer $ADMIN_TOKEN" \
     -F "file=@transporter_assignment_data.csv" | jq .
   ```
4. **Register a Standard Operator Account:**
   ```bash
   curl -s -X POST "https://freightfox-transporter-assignment-production.up.railway.app/api/v1/auth/register" \
     -H "Content-Type: application/json" \
     -d '{"username":"operator_1","email":"operator1@freightfox.com","password":"OperatorStrongPassword2026!"}' | jq .
   ```
5. **Log in as the Operator:**
   ```bash
   OPERATOR_TOKEN=$(curl -s -X POST "https://freightfox-transporter-assignment-production.up.railway.app/api/v1/auth/login" \
     -H "Content-Type: application/json" \
     -d '{"username":"operator_1","password":"OperatorStrongPassword2026!"}' | jq -r .token)
   ```
6. **Execute an Optimization Run ($K = 3$):**
   ```bash
   curl -s -X POST "https://freightfox-transporter-assignment-production.up.railway.app/api/v1/transporters/assignment" \
     -H "Authorization: Bearer $OPERATOR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"maxTransporters": 3, "objective": "LEXICOGRAPHIC_MAX_TRANSPORTERS_MIN_COST"}' | jq .
   ```
7. **Query Optimization History:**
   ```bash
   curl -s -X GET "https://freightfox-transporter-assignment-production.up.railway.app/api/v1/assignments?page=0&size=5" \
     -H "Authorization: Bearer $OPERATOR_TOKEN" | jq .
   ```

---

## Troubleshooting Guide

### 1. Java Version Incompatibility
- **Symptom:** Maven outputs `Fatal error compiling: error: release version 21 not supported` or similar compiler errors.
- **Cause:** Active Java runtime is Java 17 or older.
- **Fix:** Install Java 21 and export `JAVA_HOME` pointing to JDK 21 before building:
  ```bash
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  ./mvnw clean test
  ```

### 2. Production Secret Startup Failure
- **Symptom:** Startup fails with `Production JWT secret is missing or insecure`.
- **Cause:** `SPRING_PROFILES_ACTIVE=prod` is active, but `JWT_SECRET` is unset, shorter than 32 characters, or using the test fallback key.
- **Fix:** Generate a 256-bit key via `openssl rand -hex 32` and set it in your environment or Railway dashboard.

### 3. PostgreSQL SSL / Driver Mismatch
- **Symptom:** `Driver org.postgresql.Driver claims to not accept jdbcUrl, jdbc:h2:mem:...`
- **Cause:** Production profile attempted to fall back to H2, or the URL prefix was improperly formed.
- **Fix:** In production, ensure `DATABASE_URL` is configured. `DataSourceConfig` automatically normalizes `postgres://` into a valid `jdbc:postgresql://` URI and appends `sslmode=require` if missing.

### 4. Redis Connection Offline
- **Symptom:** Health check reports `"redis": "DOWN (using safe in-memory fallback)"`.
- **Cause:** Redis server is unreachable, port is misconfigured, or password is missing.
- **Fix:** Verify `REDIS_HOST`, `REDIS_PORT`, and `REDIS_PASSWORD`. Note that application caching and rate limiting will continue safely using local in-memory fallbacks until Redis reconnects.

### 5. HTTP 401 Unauthorized vs. HTTP 403 Forbidden
- **401 Unauthorized:** Missing, expired, or malformed JWT token in `Authorization: Bearer <token>`.
- **403 Forbidden:** The authenticated user has `ROLE_USER`, but the endpoint requires `ROLE_ADMIN` (e.g., CSV dataset upload or JSON input ingestion).

### 6. HTTP 422 Unprocessable Entity (`INFEASIBLE_ASSIGNMENT`)
- **Symptom:** `POST /api/v1/transporters/assignment` returns HTTP 422.
- **Cause:** No combination of up to $K$ transporters can cover all registered lanes (e.g., requested $K = 1$, but no single transporter submitted quotes on all lanes).
- **Fix:** Increase the $K$ parameter in `maxTransporters` or upload quote data with better coverage.

### 7. HTTP 429 Too Many Requests
- **Symptom:** Response contains HTTP 429 and header `Retry-After: <seconds>`.
- **Cause:** Request rate exceeded the allowed bucket capacity for that operation.
- **Fix:** Inspect the `Retry-After` header and wait before repeating the request.

---

## Security Hardening & Production Safeguards

- **Principle of Least Privilege:** Deny-by-default endpoint authorization. Administrative dataset modifications (`/api/v1/import/csv`, `/api/v1/transporters/input`) are restricted to `ROLE_ADMIN`.
- **BCrypt Password Hashing:** Passwords hashed using standard BCrypt with cost factor 12.
- **Defensive Data Transfer Objects (DTOs):** JPA entities are never exposed directly to REST responses, eliminating JSON serialization leaks and Hibernate lazy proxy issues.
- **Structured Error Obfuscation:** Database exception traces and internal stack traces are caught by `GlobalExceptionHandler` and masked. Responses return standardized `ErrorResponse` objects with a unique `traceId`.
- **MDC Trace Context:** Every request is assigned a unique trace ID logged via SLF4J MDC, enabling correlation across log streams without exposing sensitive tokens.
- **Unprivileged Container Execution:** Docker runtime executes under a dedicated non-root user (`appuser:appgroup`), mitigating container escape risks.

---

## Limitations & Scaling Considerations

- **Combinatorial Growth:** The exact subset enumeration algorithm evaluates $\sum_{k=1}^K \binom{M}{k}$ candidate subsets. For $M = 7$ and $K = 3$, there are only 63 subsets. For large problems (e.g., $M = 100, K = 15$), combinatorial enumeration becomes computationally intractable.
- **Configured Bounds:** The platform safeguards against CPU exhaustion via `maxCombinatorialSubsets` (capped at 50,000 subsets) and `maxRuntimeMillis` (capped at 30 seconds).
- **Enterprise Scaling Path:** For large enterprise supply chains requiring hundreds of transporters and thousands of lanes, the exact combinatorial engine should be complemented with an Integer Linear Programming (ILP) solver such as Google OR-Tools or Apache Commons Math Simplex.

---

## Future Planned Improvements

The following capabilities are identified as architectural enhancements for future releases:
- [ ] **Mixed-Integer Linear Programming (MILP) Solver:** Plug in Google OR-Tools / OptaPlanner for high-dimensional carrier-lane problem spaces ($M > 50, K > 10$).
- [ ] **Lane Capacity & Volume Constraints:** Support lane volume weighting and carrier contract minimum/maximum tonnage limits.
- [ ] **Multi-Factor Authentication (MFA):** Add TOTP-based two-factor authentication for administrative accounts.
- [ ] **Webhook Notifications:** Asynchronous event delivery via webhooks upon completion of long-running optimization jobs.
- [ ] **Export Reports:** Direct download of assignment results in Excel (`.xlsx`) and PDF formats.

---

## License & Submission Note

This project was developed by **Harsh Singh** as a technical assignment submission for **FreightFox** (*Transporter Assignment on Lanes Optimization Platform*).

All rights reserved. Not intended for unauthorized distribution.
