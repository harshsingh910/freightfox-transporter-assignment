package com.freightfox.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.assignment.dto.AssignmentRequest;
import com.freightfox.auth.AuthResponse;
import com.freightfox.auth.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CSV Import and Optimization End-to-End Test")
class CsvImportIntegrationTest {

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

        LoginRequest adminLogin = new LoginRequest("admin", "Admin@FreightFox2026!");
        MvcResult adminRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readValue(adminRes.getResponse().getContentAsString(), AuthResponse.class).token();

        String user = "csv_user_" + System.currentTimeMillis();
        MvcResult userRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.freightfox.auth.RegisterRequest(user, user + "@test.com", "Password@12345!"))))
                .andExpect(status().isCreated())
                .andReturn();
        userToken = objectMapper.readValue(userRes.getResponse().getContentAsString(), AuthResponse.class).token();
    }

    @Test
    @DisplayName("Should import the actual 9-lane CSV file and optimize with K=3 to 134876")
    void testImportActualCsvAndOptimize() throws Exception {
        InputStream is = getClass().getResourceAsStream("/data/transporter_assignment_data.csv");
        byte[] csvBytes = is.readAllBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transporter_assignment_data.csv",
                "text/csv",
                csvBytes
        );

        // 1. Upload CSV as ADMIN -> 200 OK
        mockMvc.perform(multipart("/api/v1/import/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.lanesImported").value(9))
                .andExpect(jsonPath("$.transportersImported").value(7))
                .andExpect(jsonPath("$.quotesImported").value(63));

        // 2. USER cannot upload CSV -> 403 Forbidden
        mockMvc.perform(multipart("/api/v1/import/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 3. Solve optimization with maxTransporters=3 -> exact cost 134,876.00
        AssignmentRequest request = new AssignmentRequest(3, null);
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.totalCost").value(134876))
                .andExpect(jsonPath("$.selectedTransporterCount").value(3))
                .andExpect(jsonPath("$.lanesCovered").value(9))
                .andExpect(jsonPath("$.selectedTransporters[0]").value("T1"))
                .andExpect(jsonPath("$.selectedTransporters[1]").value("T4"))
                .andExpect(jsonPath("$.selectedTransporters[2]").value("T6"));
    }
}
