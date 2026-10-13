package com.example.documentservice.integration;

import com.example.documentservice.dto.CreateTemplateDto;
import com.example.documentservice.model.entity.DocumentTemplate;
import com.example.documentservice.repository.DocumentTemplateRepository;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import com.example.sharedlib.storage.FileStorageService;
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
class DocumentTemplateControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentTemplateRepository templateRepository;

    @MockBean
    private FileStorageService fileStorageService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        templateRepository.deleteAll();
    }

    @Test
    void createTemplate_AsAdmin_Returns201AndPersists() throws Exception {
        CreateTemplateDto dto = CreateTemplateDto.builder()
                .name("Diploma Certificate")
                .description("Template for diploma certificates")
                .documentType(com.example.documentservice.model.enums.DocumentType.CERTIFICATE)
                .isPublic(true)
                .build();

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        post("/api/v1/document-templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        adminId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Diploma Certificate"));

        assertThat(templateRepository.count()).isEqualTo(1);
    }

    @Test
    void createTemplate_AsStudent_Returns403() throws Exception {
        CreateTemplateDto dto = CreateTemplateDto.builder()
                .name("Unauthorized")
                .documentType(com.example.documentservice.model.enums.DocumentType.REPORT)
                .build();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/document-templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        studentId))
                .andExpect(status().isForbidden());

        assertThat(templateRepository.count()).isEqualTo(0);
    }

    @Test
    void getAllTemplates_Authenticated_Returns200() throws Exception {
        templateRepository.save(DocumentTemplate.builder()
                .name("Template 1")
                .filePath("document-templates/test/template-1")
                .documentType(com.example.documentservice.model.enums.DocumentType.REPORT)
                .createdBy(adminId)
                .version(1)
                .isPublic(true)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/document-templates"), studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getTemplateById_Existing_Returns200() throws Exception {
        DocumentTemplate saved = templateRepository.save(DocumentTemplate.builder()
                .name("Specific Template")
                .filePath("document-templates/test/specific")
                .documentType(com.example.documentservice.model.enums.DocumentType.REPORT)
                .createdBy(adminId)
                .version(1)
                .isPublic(true)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/document-templates/" + saved.getId()), studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Specific Template"));
    }

    @Test
    void getTemplateById_NonExistent_Returns404() throws Exception {
        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/document-templates/" + UUID.randomUUID()), studentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTemplate_AsAdmin_Returns200() throws Exception {
        DocumentTemplate saved = templateRepository.save(DocumentTemplate.builder()
                .name("To Delete")
                .filePath("document-templates/test/to-delete")
                .documentType(com.example.documentservice.model.enums.DocumentType.REPORT)
                .createdBy(adminId)
                .version(1)
                .isPublic(false)
                .build());

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        delete("/api/v1/document-templates/" + saved.getId()), adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(templateRepository.count()).isEqualTo(0);
    }

    @Test
    void deleteTemplate_AsStudent_Returns403() throws Exception {
        DocumentTemplate saved = templateRepository.save(DocumentTemplate.builder()
                .name("Protected")
                .filePath("document-templates/test/protected")
                .documentType(com.example.documentservice.model.enums.DocumentType.REPORT)
                .createdBy(adminId)
                .version(1)
                .isPublic(false)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        delete("/api/v1/document-templates/" + saved.getId()), studentId))
                .andExpect(status().isForbidden());

        assertThat(templateRepository.count()).isEqualTo(1);
    }
}
