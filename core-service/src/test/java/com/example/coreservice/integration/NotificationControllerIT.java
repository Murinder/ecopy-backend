package com.example.coreservice.integration;

import com.example.coreservice.model.entity.User;
import com.example.coreservice.repository.UserRepository;
import com.example.coreservice.search.SearchableDocumentRepository;
import com.example.coreservice.service.EmailService;
import com.example.coreservice.service.SearchService;
import com.example.sharedlib.enums.UserRole;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.containers.RedisTestContainer;
import com.example.testsupport.security.TestSecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class NotificationControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

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

    private User testUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");

        testUser = userRepository.save(User.builder()
                .email("notify-user@test.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.STUDENT)
                .isActive(true)
                .firstName("Test")
                .lastName("User")
                .build());
    }

    private void insertNotification(UUID id, UUID userId, String title, String message, boolean isRead) {
        jdbcTemplate.update(
                "INSERT INTO notifications (id, user_id, title, message, type, is_read, created_at) VALUES (?::uuid, ?::uuid, ?, ?, 'SYSTEM'::notification_type, ?, NOW())",
                id.toString(), userId.toString(), title, message, isRead
        );
    }

    @Test
    void getUserNotifications_ReturnsNotifications() throws Exception {
        insertNotification(UUID.randomUUID(), testUser.getId(), "Test Notification", "Test message", false);

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/notifications/user/" + testUser.getId()),
                        testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Test Notification"));
    }

    @Test
    void getUnreadNotifications_ReturnsOnlyUnread() throws Exception {
        insertNotification(UUID.randomUUID(), testUser.getId(), "Unread", "msg", false);
        insertNotification(UUID.randomUUID(), testUser.getId(), "Read", "msg", true);

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/notifications/user/" + testUser.getId() + "/unread"),
                        testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Unread"));
    }

    @Test
    void getUnreadCount_ReturnsCorrectCount() throws Exception {
        insertNotification(UUID.randomUUID(), testUser.getId(), "N1", "m", false);
        insertNotification(UUID.randomUUID(), testUser.getId(), "N2", "m", false);
        insertNotification(UUID.randomUUID(), testUser.getId(), "N3", "m", true);

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/notifications/user/" + testUser.getId() + "/unread-count"),
                        testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    void markAsRead_UpdatesNotification() throws Exception {
        UUID notifId = UUID.randomUUID();
        insertNotification(notifId, testUser.getId(), "To Read", "msg", false);

        mockMvc.perform(TestSecurityHelper.withStudent(
                        put("/api/v1/notifications/" + notifId + "/mark-read"),
                        testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getUserNotifications_WithoutAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/user/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
