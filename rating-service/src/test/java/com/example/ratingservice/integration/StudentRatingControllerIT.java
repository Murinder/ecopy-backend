package com.example.ratingservice.integration;

import com.example.ratingservice.model.StudentRating;
import com.example.ratingservice.repository.StudentRatingRepository;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class StudentRatingControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRatingRepository studentRatingRepository;

    private final UUID studentId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        studentRatingRepository.deleteAll();
    }

    @Test
    void getStudentRating_ExistingStudent_Returns200() throws Exception {
        studentRatingRepository.save(StudentRating.builder()
                .userId(studentId)
                .totalScore(new BigDecimal("85.5"))
                .updatedAt(OffsetDateTime.now())
                .build());

                mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/ratings/students/" + studentId), requesterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(studentId.toString()));
    }

    @Test
    void getStudentRating_NonExistentStudent_ReturnsDefaultRating() throws Exception {
        UUID unknownUserId = UUID.randomUUID();

                mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/ratings/students/" + unknownUserId), requesterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(unknownUserId.toString()))
                .andExpect(jsonPath("$.data.totalScore").value(0))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));
    }

    @Test
    void getTopStudents_ReturnsOrderedList() throws Exception {
        studentRatingRepository.save(StudentRating.builder()
                .userId(UUID.randomUUID())
                .totalScore(new BigDecimal("90.0"))
                .updatedAt(OffsetDateTime.now())
                .build());
        studentRatingRepository.save(StudentRating.builder()
                .userId(UUID.randomUUID())
                .totalScore(new BigDecimal("95.0"))
                .updatedAt(OffsetDateTime.now())
                .build());

                mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/ratings/students/top").param("limit", "10"), requesterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getStudentRating_WithoutAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/ratings/students/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
