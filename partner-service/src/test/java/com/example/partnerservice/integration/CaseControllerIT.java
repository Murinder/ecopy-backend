package com.example.partnerservice.integration;

import com.example.partnerservice.dto.CreateCaseDto;
import com.example.partnerservice.model.Partner;
import com.example.partnerservice.model.Case;
import com.example.partnerservice.repository.CaseRepository;
import com.example.partnerservice.repository.PartnerRepository;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CaseControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    private Partner testPartner;

    @BeforeEach
    void setUp() {
        caseRepository.deleteAll();
        partnerRepository.deleteAll();
        testPartner = partnerRepository.save(Partner.builder()
                .companyName("Test Corp")
                .contactInfo("test@corp.com")
                .isActive(true)
                .build());
    }

    @Test
    void createCase_ValidData_Returns201AndPersists() throws Exception {
        CreateCaseDto dto = CreateCaseDto.builder()
                .partnerId(testPartner.getId())
                .title("ML Challenge")
                .description("Build a classification model")
                .difficulty("INTERMEDIATE")
                .build();

        mockMvc.perform(TestSecurityHelper.withPartner(
                        post("/api/v1/cases")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("ML Challenge"));

        assertThat(caseRepository.count()).isEqualTo(1);
    }

    @Test
    void getAllCases_Returns200() throws Exception {
        caseRepository.save(Case.builder()
                .partner(testPartner)
                .title("Case 1")
                .description("Desc")
                .isActive(true)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/cases"), UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getActiveCases_ReturnsOnlyActive() throws Exception {
        caseRepository.save(Case.builder()
                .partner(testPartner).title("Active").description("d").isActive(true).build());
        caseRepository.save(Case.builder()
                .partner(testPartner).title("Inactive").description("d").isActive(false).build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/cases/active"), UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Active"));
    }

    @Test
    void getCaseById_NonExistent_Returns404() throws Exception {
        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/cases/" + UUID.randomUUID()), UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
