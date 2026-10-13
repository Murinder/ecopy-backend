package com.example.eventservice.event;

import com.example.sharedlib.config.RabbitMqAutoConfiguration;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EventEventPublishingIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        try {
            rabbitTemplate.execute(channel -> {
                channel.queuePurge(RabbitMqAutoConfiguration.QUEUE_EVENT_CREATED_SEARCH);
                channel.queuePurge(RabbitMqAutoConfiguration.QUEUE_EVENT_CREATED_NOTIFICATION);
                return null;
            });
        } catch (Exception ignored) {}
    }

    @Test
    void createEvent_PublishesEventCreatedToSearchQueue() throws Exception {
        Map<String, Object> eventDto = Map.of(
                "title", "Spring Hackathon",
                "description", "Annual Spring Boot hackathon",
                "startDate", OffsetDateTime.now().plusDays(30).toString(),
                "endDate", OffsetDateTime.now().plusDays(32).toString(),
                "format", "ONLINE"
        );

        mockMvc.perform(TestSecurityHelper.withLecturer(
                        post("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(eventDto)),
                        UUID.randomUUID()))
                .andExpect(status().isCreated());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var message = rabbitTemplate.receive(RabbitMqAutoConfiguration.QUEUE_EVENT_CREATED_SEARCH, 100);
            assertThat(message).isNotNull();
            String body = new String(message.getBody());
            assertThat(body).contains("Spring Hackathon");
        });
    }

    @Test
    void createEvent_PublishesEventCreatedToNotificationQueue() throws Exception {
        Map<String, Object> eventDto = Map.of(
                "title", "Notification Test Event",
                "description", "Test",
                "startDate", OffsetDateTime.now().plusDays(10).toString(),
                "endDate", OffsetDateTime.now().plusDays(12).toString(),
                "format", "ONLINE"
        );

        mockMvc.perform(TestSecurityHelper.withLecturer(
                        post("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(eventDto)),
                        UUID.randomUUID()))
                .andExpect(status().isCreated());

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var message = rabbitTemplate.receive(RabbitMqAutoConfiguration.QUEUE_EVENT_CREATED_NOTIFICATION, 100);
            assertThat(message).isNotNull();
        });
    }
}
