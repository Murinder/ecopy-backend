package com.example.ratingservice.integration;

import com.example.ratingservice.dto.RatingCriteriaDto;
import com.example.ratingservice.model.RatingCriteria;
import com.example.ratingservice.model.RatingCriteriaType;
import com.example.ratingservice.repository.RatingCriteriaRepository;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RatingCriteriaControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RatingCriteriaRepository criteriaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID adminId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE rating_history, rating_criteria CASCADE");
    }

    @Test
    void getAllCriteria_Authenticated_Returns200() throws Exception {
        criteriaRepository.save(RatingCriteria.builder()
                .name("Project Completion")
                .weight(new BigDecimal("1.5"))
                .isActive(true)
                .criteriaType(RatingCriteriaType.STUDENT)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/ratings/criteria"), studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createCriteria_AsAdmin_Returns201AndPersists() throws Exception {
        RatingCriteriaDto dto = RatingCriteriaDto.builder()
                .name("Task Completion")
                .description("Points for completing tasks")
                .weight(new BigDecimal("2.0"))
                .isActive(true)
                .criteriaType(RatingCriteriaType.STUDENT)
                .build();

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        post("/api/v1/ratings/criteria")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        adminId))
                .andExpect(status().isCreated());

        assertThat(criteriaRepository.count()).isEqualTo(1);
    }

    @Test
    void createCriteria_AsStudent_Returns403() throws Exception {
        RatingCriteriaDto dto = RatingCriteriaDto.builder()
                .name("Unauthorized")
                .weight(new BigDecimal("1.0"))
                .criteriaType(RatingCriteriaType.STUDENT)
                .build();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/ratings/criteria")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        studentId))
                .andExpect(status().isForbidden());

        assertThat(criteriaRepository.count()).isEqualTo(0);
    }

    @Test
    void updateCriteria_AsAdmin_Returns200AndUpdatesDb() throws Exception {
        RatingCriteria saved = criteriaRepository.save(RatingCriteria.builder()
                .name("Old Name")
                .weight(new BigDecimal("1.0"))
                .isActive(true)
                .criteriaType(RatingCriteriaType.STUDENT)
                .build());

        RatingCriteriaDto updateDto = RatingCriteriaDto.builder()
                .name("Updated Name")
                .weight(new BigDecimal("3.0"))
                .isActive(false)
                .criteriaType(RatingCriteriaType.STUDENT)
                .build();

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        put("/api/v1/ratings/criteria/" + saved.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDto)),
                        adminId))
                .andExpect(status().isOk());

        RatingCriteria updated = criteriaRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
    }
}
