package com.example.projectservice.integration;

import com.example.projectservice.client.UserServiceClient;
import com.example.projectservice.dto.ProjectTemplateDto;
import com.example.projectservice.model.ProjectTemplate;
import com.example.projectservice.model.enums.TemplateType;
import com.example.projectservice.repository.ProjectTemplateRepository;
import com.example.sharedlib.storage.FileStorageService;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ProjectTemplateControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectTemplateRepository projectTemplateRepository;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private FileStorageService fileStorageService;

    private static final UUID LECTURER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        projectTemplateRepository.deleteAll();
    }

    private ProjectTemplateDto buildTemplateDto() {
        return ProjectTemplateDto.builder()
                .name("Diploma Template")
                .description("Template for diploma projects")
                .templateType(TemplateType.DIPLOMA)
                .config("{\"stages\": [\"research\", \"implementation\", \"defense\"]}")
                .createdBy(LECTURER_ID)
                .build();
    }

    @Test
    void createTemplate_AsLecturer_ReturnsCreatedTemplate() throws Exception {
        ProjectTemplateDto dto = buildTemplateDto();

        mockMvc.perform(TestSecurityHelper.withLecturer(
                        post("/api/v1/project-templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        LECTURER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Diploma Template"))
                .andExpect(jsonPath("$.templateType").value("DIPLOMA"));

        assertThat(projectTemplateRepository.count()).isEqualTo(1);
    }

    @Test
    void createTemplate_AsStudent_Returns403() throws Exception {
        ProjectTemplateDto dto = buildTemplateDto();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/project-templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        STUDENT_ID))
                .andExpect(status().isForbidden());

        assertThat(projectTemplateRepository.count()).isEqualTo(0);
    }

    @Test
    void getTemplateById_ReturnsTemplate() throws Exception {
        ProjectTemplate template = projectTemplateRepository.save(ProjectTemplate.builder()
                .name("Research Template")
                .description("For research projects")
                .templateType(TemplateType.RESEARCH)
                .config("{}")
                .createdBy(LECTURER_ID)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/project-templates/" + template.getId()),
                        STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Research Template"))
                .andExpect(jsonPath("$.templateType").value("RESEARCH"));
    }

    @Test
    void getAllTemplates_ReturnsList() throws Exception {
        projectTemplateRepository.save(ProjectTemplate.builder()
                .name("Template 1")
                .description("desc")
                .templateType(TemplateType.DIPLOMA)
                .config("{}")
                .createdBy(LECTURER_ID)
                .build());
        projectTemplateRepository.save(ProjectTemplate.builder()
                .name("Template 2")
                .description("desc")
                .templateType(TemplateType.HACKATHON)
                .config("{}")
                .createdBy(LECTURER_ID)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/project-templates"),
                        STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getTemplateById_NotFound_Returns404() throws Exception {
        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/project-templates/" + UUID.randomUUID()),
                        STUDENT_ID))
                .andExpect(status().isNotFound());
    }
}
