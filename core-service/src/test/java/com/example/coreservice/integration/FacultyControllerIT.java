package com.example.coreservice.integration;

import com.example.coreservice.dto.FacultyDto;
import com.example.coreservice.model.entity.Faculty;
import com.example.coreservice.repository.FacultyRepository;
import com.example.coreservice.search.SearchableDocumentRepository;
import com.example.coreservice.service.EmailService;
import com.example.coreservice.service.SearchService;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.containers.RedisTestContainer;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FacultyControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SearchableDocumentRepository searchableDocumentRepository;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private EmailService emailService;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer::getHost);
        registry.add("spring.data.redis.port", RedisTestContainer::getPort);
    }

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE faculties CASCADE");
    }

    // ---- Create ----

    @Test
    void createFaculty_AsAdmin_Returns201AndPersists() throws Exception {
        FacultyDto dto = FacultyDto.builder()
                .name("Faculty of Computer Science")
                .code("FCS")
                .description("Computer Science Faculty")
                .build();

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        post("/api/v1/faculties")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        ADMIN_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Faculty of Computer Science"))
                .andExpect(jsonPath("$.code").value("FCS"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        assertThat(facultyRepository.count()).isEqualTo(1);
        Faculty saved = facultyRepository.findByCode("FCS").orElseThrow();
        assertThat(saved.getName()).isEqualTo("Faculty of Computer Science");
    }

    @Test
    void createFaculty_AsStudent_Returns403() throws Exception {
        FacultyDto dto = FacultyDto.builder()
                .name("Unauthorized Faculty")
                .code("UF")
                .build();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/faculties")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        STUDENT_ID))
                .andExpect(status().isForbidden());

        assertThat(facultyRepository.count()).isEqualTo(0);
    }

    @Test
    void createFaculty_DuplicateCode_Returns400() throws Exception {
        FacultyDto dto = FacultyDto.builder()
                .name("Faculty One")
                .code("DUP")
                .build();

        // First creation
        mockMvc.perform(TestSecurityHelper.withAdmin(
                        post("/api/v1/faculties")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        ADMIN_ID))
                .andExpect(status().isCreated());

        // Duplicate code
        FacultyDto duplicate = FacultyDto.builder()
                .name("Faculty Two")
                .code("DUP")
                .build();

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        post("/api/v1/faculties")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicate)),
                        ADMIN_ID))
                .andExpect(status().isBadRequest());

        assertThat(facultyRepository.count()).isEqualTo(1);
    }

    @Test
    void createFaculty_Unauthenticated_Returns403() throws Exception {
        FacultyDto dto = FacultyDto.builder()
                .name("No Auth Faculty")
                .code("NAF")
                .build();

        mockMvc.perform(post("/api/v1/faculties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // ---- Read ----

    @Test
    void getAllFaculties_Authenticated_ReturnsListAndStatus200() throws Exception {
        createFacultyInDb("Faculty A", "FA");
        createFacultyInDb("Faculty B", "FB");

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/faculties"), STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getFacultyById_ExistingFaculty_Returns200() throws Exception {
        Faculty faculty = createFacultyInDb("Faculty X", "FX");

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/faculties/" + faculty.getId()), STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Faculty X"))
                .andExpect(jsonPath("$.code").value("FX"));
    }

    @Test
    void getFacultyById_NonExistent_Returns404() throws Exception {
        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/faculties/" + UUID.randomUUID()), STUDENT_ID))
                .andExpect(status().isNotFound());
    }

    // ---- Update ----

    @Test
    void updateFaculty_AsAdmin_Returns200AndUpdatesDb() throws Exception {
        Faculty faculty = createFacultyInDb("Old Name", "OLD");

        FacultyDto updateDto = FacultyDto.builder()
                .name("New Name")
                .code("NEW")
                .description("Updated description")
                .build();

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        put("/api/v1/faculties/" + faculty.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDto)),
                        ADMIN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.code").value("NEW"));

        Faculty updated = facultyRepository.findById(faculty.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void updateFaculty_NonExistent_Returns404() throws Exception {
        FacultyDto updateDto = FacultyDto.builder()
                .name("Ghost")
                .code("GH")
                .build();

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        put("/api/v1/faculties/" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDto)),
                        ADMIN_ID))
                .andExpect(status().isNotFound());
    }

    // ---- Delete ----

    @Test
    void deleteFaculty_AsAdmin_Returns204AndRemovesFromDb() throws Exception {
        Faculty faculty = createFacultyInDb("To Delete", "DEL");
        assertThat(facultyRepository.count()).isEqualTo(1);

        mockMvc.perform(TestSecurityHelper.withAdmin(
                        delete("/api/v1/faculties/" + faculty.getId()), ADMIN_ID))
                .andExpect(status().isNoContent());

        assertThat(facultyRepository.count()).isEqualTo(0);
    }

    @Test
    void deleteFaculty_AsStudent_Returns403() throws Exception {
        Faculty faculty = createFacultyInDb("Protected", "PRO");

        mockMvc.perform(TestSecurityHelper.withStudent(
                        delete("/api/v1/faculties/" + faculty.getId()), STUDENT_ID))
                .andExpect(status().isForbidden());

        assertThat(facultyRepository.count()).isEqualTo(1);
    }

    @Test
    void deleteFaculty_NonExistent_Returns404() throws Exception {
        mockMvc.perform(TestSecurityHelper.withAdmin(
                        delete("/api/v1/faculties/" + UUID.randomUUID()), ADMIN_ID))
                .andExpect(status().isNotFound());
    }

    // ---- Helper ----

    private Faculty createFacultyInDb(String name, String code) {
        Faculty faculty = Faculty.builder()
                .name(name)
                .code(code)
                .build();
        return facultyRepository.save(faculty);
    }
}
