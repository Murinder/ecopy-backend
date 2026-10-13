package com.example.coreservice.event;

import com.example.coreservice.dto.RegisterRequest;
import com.example.coreservice.listener.NotificationEventListener;
import com.example.coreservice.listener.ProjectEventListener;
import com.example.coreservice.listener.SearchEventListener;
import com.example.coreservice.search.SearchableDocumentRepository;
import com.example.coreservice.service.EmailService;
import com.example.coreservice.service.SearchService;
import com.example.sharedlib.config.RabbitMqAutoConfiguration;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.containers.RedisTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UserEventPublishingIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

    // Mock listeners to prevent them from consuming messages before the test
    @MockBean
    private SearchEventListener searchEventListener;
    @MockBean
    private NotificationEventListener notificationEventListener;
    @MockBean
    private ProjectEventListener projectEventListener;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer::getHost);
        registry.add("spring.data.redis.port", RedisTestContainer::getPort);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
        // Purge queues before test
        try {
            rabbitTemplate.execute(channel -> {
                channel.queuePurge(RabbitMqAutoConfiguration.QUEUE_USER_CREATED_PORTFOLIO);
                channel.queuePurge(RabbitMqAutoConfiguration.QUEUE_USER_CREATED_SEARCH);
                return null;
            });
        } catch (Exception ignored) {}
    }

    @Test
    void register_PublishesUserCreatedEvent_ToPortfolioQueue() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("event-test@university.edu")
                .password("SecurePass1")
                .firstName("Event")
                .lastName("Test")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verify message arrived in portfolio queue (service sends UserEvent as payload)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var message = rabbitTemplate.receive(RabbitMqAutoConfiguration.QUEUE_USER_CREATED_PORTFOLIO, 100);
            assertThat(message).isNotNull();
            assertThat(message.getBody()).isNotEmpty();
        });
    }

    @Test
    void register_PublishesUserCreatedEvent_ToSearchQueue() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("search-test@university.edu")
                .password("SecurePass1")
                .firstName("Search")
                .lastName("Test")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var message = rabbitTemplate.receive(RabbitMqAutoConfiguration.QUEUE_USER_CREATED_SEARCH, 100);
            assertThat(message).isNotNull();
            assertThat(message.getBody()).isNotEmpty();
        });
    }
}
