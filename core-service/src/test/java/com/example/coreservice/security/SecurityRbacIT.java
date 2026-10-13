package com.example.coreservice.security;

import com.example.coreservice.repository.UserRepository;
import com.example.coreservice.search.SearchableDocumentRepository;
import com.example.coreservice.service.EmailService;
import com.example.coreservice.service.SearchService;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.containers.RedisTestContainer;
import com.example.testsupport.security.TestSecurityHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityRbacIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    // === Public endpoints (no auth required) ===

    @Test
    void authRegister_IsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"test\"}"))
                .andExpect(status().is4xxClientError()); // 400 validation, not 401/403
    }

    @Test
    void authLogin_IsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"test\"}"))
                .andExpect(status().is4xxClientError()); // bad credentials, not 403
    }

    @Test
    void actuator_IsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    // === Protected endpoints (require authentication) ===

    @Test
    void faculties_WithoutAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/faculties"))
                .andExpect(status().isForbidden());
    }

    @Test
    void faculties_WithAuth_Returns200() throws Exception {
        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/faculties"), UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void notifications_WithoutAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/user/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboards_WithoutAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards"))
                .andExpect(status().isForbidden());
    }

    // === Admin-only endpoints ===

    @Test
    void createFaculty_AsStudent_ReturnsError() throws Exception {
        // @AdminOnly uses @PreAuthorize which throws AccessDeniedException -> 500
        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/faculties")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"test\",\"code\":\"T\"}"),
                        UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createFaculty_AsAdmin_NotForbidden() throws Exception {
        mockMvc.perform(TestSecurityHelper.withAdmin(
                        post("/api/v1/faculties")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"test\",\"code\":\"RBAC\"}"),
                        UUID.randomUUID()))
                .andExpect(status().isCreated());
    }
}
