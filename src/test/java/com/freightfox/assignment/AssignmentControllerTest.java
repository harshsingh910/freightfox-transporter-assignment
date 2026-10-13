package com.freightfox.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.assignment.dto.AssignmentRequest;
import com.freightfox.assignment.dto.InputLaneDto;
import com.freightfox.assignment.dto.InputLaneQuoteDto;
import com.freightfox.assignment.dto.InputTransporterDto;
import com.freightfox.assignment.dto.TransportersInputRequest;
import com.freightfox.auth.AuthResponse;
import com.freightfox.auth.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AssignmentController End-to-End Integration Tests")
class AssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.freightfox.assignment.repository.AssignmentRunRepository assignmentRunRepository;

    @Autowired
    private com.freightfox.quote.TransporterQuoteRepository quoteRepository;

    @Autowired
    private com.freightfox.lane.LaneRepository laneRepository;

    @Autowired
    private com.freightfox.transporter.TransporterRepository transporterRepository;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        assignmentRunRepository.deleteAll();
        quoteRepository.deleteAll();
        laneRepository.deleteAll();
        transporterRepository.deleteAll();

        // Admin login
        LoginRequest adminLogin = new LoginRequest("admin", "Admin@FreightFox2026!");
        MvcResult adminRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readValue(adminRes.getResponse().getContentAsString(), AuthResponse.class).token();

        // User registration / login
        String user = "user_" + System.currentTimeMillis();
        MvcResult userRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.freightfox.auth.RegisterRequest(user, user + "@test.com", "Password@12345!"))))
                .andExpect(status().isCreated())
                .andReturn();
        userToken = objectMapper.readValue(userRes.getResponse().getContentAsString(), AuthResponse.class).token();
    }

    @Test
    @DisplayName("Should submit input data as ADMIN and compute optimal assignment as USER")
    void testInputAndAssignmentLifecycle() throws Exception {
        // Prepare 5 lanes
        List<InputLaneDto> lanes = List.of(
                new InputLaneDto(1, "Mumbai", "Delhi"),
                new InputLaneDto(2, "Delhi", "Bangalore"),
                new InputLaneDto(3, "Chennai", "Kolkata"),
                new InputLaneDto(4, "Pune", "Hyderabad"),
                new InputLaneDto(5, "Ahmedabad", "Jaipur")
        );

        // Prepare transporters with quotes (Test Case 1)
        int[][] quotes = {
                {20835, 10512, 22105, 42481, 19862}, // T1
                {48844, 31326, 18640, 45828, 18297}, // T2
                {39020, 20648, 31438, 36447, 12789}, // T3
                {14400, 44514, 14316, 10678, 13032}, // T4
                {11601, 19760, 40870, 20635, 26421}, // T5
                {35095, 12494, 17808, 36210, 39444}, // T6
                {26070, 41098, 20932, 16897, 27938}  // T7
        };

        List<InputTransporterDto> transporters = new ArrayList<>();
        for (int t = 1; t <= 7; t++) {
            List<InputLaneQuoteDto> laneQuotes = new ArrayList<>();
            for (int l = 1; l <= 5; l++) {
                laneQuotes.add(new InputLaneQuoteDto(l, BigDecimal.valueOf(quotes[t - 1][l - 1])));
            }
            transporters.add(new InputTransporterDto(t, "Transporter T" + t, laneQuotes));
        }

        TransportersInputRequest inputRequest = new TransportersInputRequest(lanes, transporters);

        // 1. Submit input as ADMIN -> 200 OK
        mockMvc.perform(post("/api/v1/transporters/input")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Input data saved successfully."));

        // 2. Submit input as USER -> 403 Forbidden
        mockMvc.perform(post("/api/v1/transporters/input")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputRequest)))
                .andExpect(status().isForbidden());

        // 3. Compute assignment with maxTransporters=3 as USER -> 200 OK
        AssignmentRequest assignReq = new AssignmentRequest(3, null);
        MvcResult assignRes = mockMvc.perform(post("/api/v1/transporters/assignment")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.lanesCovered").value(5))
                .andExpect(jsonPath("$.selectedTransporterCount").value(3))
                .andExpect(jsonPath("$.totalCost").value(60139))
                .andReturn();

        // 4. Retrieve saved assignment by runId
        Number runId = objectMapper.readTree(assignRes.getResponse().getContentAsString())
                .get("assignmentRunId").numberValue();

        mockMvc.perform(get("/api/v1/assignments/" + runId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(60139));

        // 5. Query lanes and transporters
        mockMvc.perform(get("/api/v1/lanes?page=0&size=2&sortDir=DESC")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(5));

        mockMvc.perform(get("/api/v1/transporters")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(7));
    }

    @Test
    @DisplayName("Should reject input data with negative quotes with 400 Bad Request")
    void testNegativeQuoteRejected() throws Exception {
        TransportersInputRequest badRequest = new TransportersInputRequest(
                List.of(new InputLaneDto(1, "Mumbai", "Delhi")),
                List.of(new InputTransporterDto(1, "T1", List.of(new InputLaneQuoteDto(1, new BigDecimal("-100")))))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should return 422 Unprocessable Entity when lane coverage is infeasible")
    void testInfeasibleCoverage() throws Exception {
        // Setup sparse problem where Lane 1 is quoted only by T1 and Lane 2 only by T2
        TransportersInputRequest sparseInput = new TransportersInputRequest(
                List.of(
                        new InputLaneDto(1, "City A", "City B"),
                        new InputLaneDto(2, "City C", "City D")
                ),
                List.of(
                        new InputTransporterDto(1, "T1", List.of(new InputLaneQuoteDto(1, new BigDecimal("100")))),
                        new InputTransporterDto(2, "T2", List.of(new InputLaneQuoteDto(2, new BigDecimal("200"))))
                )
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sparseInput)))
                .andExpect(status().isOk());

        // With maxTransporters=1, it is impossible to cover both lanes -> 422 Unprocessable Entity
        AssignmentRequest request = new AssignmentRequest(1, null);
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INFEASIBLE_ASSIGNMENT"));
    }

    @Test
    @DisplayName("Regression Test: Should list paginated assignment history without LazyInitializationException")
    void testPaginatedAssignmentRunsWithoutLazyInitializationException() throws Exception {
        // 1. Submit input data
        List<InputLaneDto> lanes = List.of(
                new InputLaneDto(1, "Mumbai", "Delhi"),
                new InputLaneDto(2, "Delhi", "Bangalore"),
                new InputLaneDto(3, "Chennai", "Kolkata")
        );

        int[][] quotes = {
                {20000, 10000, 22000}, // T1
                {48000, 31000, 18000}, // T2
                {39000, 20000, 31000}  // T3
        };

        List<InputTransporterDto> transporters = new ArrayList<>();
        for (int t = 1; t <= 3; t++) {
            List<InputLaneQuoteDto> laneQuotes = new ArrayList<>();
            for (int l = 1; l <= 3; l++) {
                laneQuotes.add(new InputLaneQuoteDto(l, BigDecimal.valueOf(quotes[t - 1][l - 1])));
            }
            transporters.add(new InputTransporterDto(t, "Transporter T" + t, laneQuotes));
        }

        mockMvc.perform(post("/api/v1/transporters/input")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransportersInputRequest(lanes, transporters))))
                .andExpect(status().isOk());

        // 2. Create Assignment Run 1 (maxTransporters=2)
        MvcResult run1Res = mockMvc.perform(post("/api/v1/transporters/assignment")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignmentRequest(2, null))))
                .andExpect(status().isOk())
                .andReturn();
        Number run1Id = objectMapper.readTree(run1Res.getResponse().getContentAsString()).get("assignmentRunId").numberValue();

        // 3. Create Assignment Run 2 (maxTransporters=3)
        MvcResult run2Res = mockMvc.perform(post("/api/v1/transporters/assignment")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignmentRequest(3, null))))
                .andExpect(status().isOk())
                .andReturn();
        Number run2Id = objectMapper.readTree(run2Res.getResponse().getContentAsString()).get("assignmentRunId").numberValue();

        // 4. Query paginated history: Page 0 with size=10 (Contains both runs, no HTTP 500)
        mockMvc.perform(get("/api/v1/assignments?page=0&size=10")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(run2Id.longValue()))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].lanesCovered").value(3))
                .andExpect(jsonPath("$.content[0].totalCost").exists())
                .andExpect(jsonPath("$.content[0].laneAssignments.length()").value(3))
                .andExpect(jsonPath("$.content[0].laneAssignments[0].origin").exists())
                .andExpect(jsonPath("$.content[0].laneAssignments[0].destination").exists())
                .andExpect(jsonPath("$.content[0].laneAssignments[0].quoteAmount").exists())
                .andExpect(jsonPath("$.content[0].laneAssignments[0].transporterName").exists())
                .andExpect(jsonPath("$.content[1].id").value(run1Id.longValue()))
                .andExpect(jsonPath("$.content[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[1].laneAssignments.length()").value(3));

        // 5. Query paginated history: Page 0 with size=1 (First page has run 2)
        mockMvc.perform(get("/api/v1/assignments?page=0&size=1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(run2Id.longValue()))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        // 6. Query paginated history: Page 1 with size=1 (Second page has run 1)
        mockMvc.perform(get("/api/v1/assignments?page=1&size=1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(run1Id.longValue()));
    }
}
