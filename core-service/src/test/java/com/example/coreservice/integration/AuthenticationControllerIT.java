package com.example.coreservice.integration;

import com.example.coreservice.dto.LoginRequest;
import com.example.coreservice.dto.RegisterRequest;
import com.example.coreservice.dto.RefreshTokenRequest;
import com.example.coreservice.model.entity.User;
import com.example.coreservice.repository.UserRepository;
import com.example.coreservice.search.SearchableDocumentRepository;
import com.example.coreservice.service.EmailService;
import com.example.coreservice.service.SearchService;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.containers.RedisTestContainer;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthenticationControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    }

    // ---- Register ----

    @Test
    void register_WithValidData_Returns201AndPersistsUser() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("student@university.edu")
                .password("SecurePass1")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+77001234567")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("USER_REGISTERED"))
                .andExpect(jsonPath("$.data.email").value("student@university.edu"))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Doe"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));

        assertThat(userRepository.count()).isEqualTo(1);
        User saved = userRepository.findByEmail("student@university.edu").orElseThrow();
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    void register_DuplicateEmail_Returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("dup@university.edu")
                .password("SecurePass1")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void register_WeakPassword_Returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("weak@university.edu")
                .password("short")
                .firstName("John")
                .lastName("Doe")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(userRepository.count()).isEqualTo(0);
    }

    // ---- Login ----

    @Test
    void login_WithValidCredentials_Returns200WithTokens() throws Exception {
        // Register first
        registerTestUser("login@university.edu", "SecurePass1");

        LoginRequest loginRequest = LoginRequest.builder()
                .email("login@university.edu")
                .password("SecurePass1")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void login_WithInvalidPassword_Returns401() throws Exception {
        registerTestUser("bad@university.edu", "SecurePass1");

        LoginRequest loginRequest = LoginRequest.builder()
                .email("bad@university.edu")
                .password("WrongPass1")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_WithNonExistentEmail_Returns401() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("noone@university.edu")
                .password("SecurePass1")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ---- Refresh Token ----

    @Test
    void refreshToken_WithValidToken_Returns200WithNewTokens() throws Exception {
        registerTestUser("refresh@university.edu", "SecurePass1");

        // Login to get tokens
        LoginRequest loginRequest = LoginRequest.builder()
                .email("refresh@university.edu")
                .password("SecurePass1")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.refreshToken");

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TOKEN_REFRESHED"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void refreshToken_WithInvalidToken_Returns401() throws Exception {
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken("invalid-token-value");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---- Profile ----

    @Test
    void getProfile_AuthenticatedUser_Returns200() throws Exception {
        registerTestUser("profile@university.edu", "SecurePass1");
        User user = userRepository.findByEmail("profile@university.edu").orElseThrow();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/auth/profile/" + user.getId()), user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PROFILE_RETRIEVED"))
                .andExpect(jsonPath("$.data.email").value("profile@university.edu"));
    }

    @Test
    void getProfile_UnauthenticatedUser_ReturnsError() throws Exception {
        // /api/v1/auth/** is permitAll at filter level, but @AuthenticatedOnly uses @PreAuthorize
        // which throws AccessDeniedException — handled as 500 by global exception handler
        mockMvc.perform(get("/api/v1/auth/profile/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfile_NonExistentUser_Returns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/auth/profile/" + randomId), randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---- Helper ----

    private void registerTestUser(String email, String password) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .firstName("Test")
                .lastName("User")
                .build();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
