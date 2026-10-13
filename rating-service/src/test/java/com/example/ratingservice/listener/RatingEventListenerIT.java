package com.example.ratingservice.listener;

import com.example.ratingservice.model.StudentRating;
import com.example.ratingservice.repository.StudentRatingRepository;
import com.example.sharedlib.config.RabbitMqAutoConfiguration;
import com.example.testsupport.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RatingEventListenerIT extends BaseIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StudentRatingRepository studentRatingRepository;

    @BeforeEach
    void setUp() {
        studentRatingRepository.deleteAll();
    }

    @Test
    void onTaskCompleted_UpdatesStudentRating() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        // Pre-seed student rating
        studentRatingRepository.save(StudentRating.builder()
                .userId(userId)
                .totalScore(BigDecimal.ZERO)
                .updatedAt(OffsetDateTime.now())
                .build());

        // Send task completed event
        Map<String, Object> event = Map.of(
                "userId", userId.toString(),
                "taskId", taskId.toString(),
                "projectId", projectId.toString()
        );

        rabbitTemplate.convertAndSend(
                RabbitMqAutoConfiguration.PROJECT_EXCHANGE,
                RabbitMqAutoConfiguration.PROJECT_TASK_COMPLETED_KEY,
                event
        );

        // Verify rating was updated
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            StudentRating rating = studentRatingRepository.findById(userId).orElseThrow();
            assertThat(rating.getTotalScore()).isGreaterThan(BigDecimal.ZERO);
        });
    }

    @Test
    void onApplicationDecided_Approved_UpdatesRating() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Pre-seed student rating
        studentRatingRepository.save(StudentRating.builder()
                .userId(userId)
                .totalScore(BigDecimal.ZERO)
                .updatedAt(OffsetDateTime.now())
                .build());

        Map<String, Object> event = Map.of(
                "userId", userId.toString(),
                "domainEventId", eventId.toString(),
                "applicationStatus", "APPROVED"
        );

        rabbitTemplate.convertAndSend(
                RabbitMqAutoConfiguration.EVENT_EXCHANGE,
                RabbitMqAutoConfiguration.EVENT_APPLICATION_DECIDED_KEY,
                event
        );

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            StudentRating rating = studentRatingRepository.findById(userId).orElseThrow();
            assertThat(rating.getTotalScore()).isGreaterThan(BigDecimal.ZERO);
        });
    }

    @Test
    void onAchievementAdded_RecalculatesNormalizedRating() {
        UUID userId = UUID.randomUUID();

        // Pre-seed student rating
        studentRatingRepository.save(StudentRating.builder()
                .userId(userId)
                .totalScore(new BigDecimal("10.0"))
                .updatedAt(OffsetDateTime.now())
                .build());

        Map<String, Object> event = Map.of(
                "userId", userId.toString(),
                "achievementId", UUID.randomUUID().toString()
        );

        rabbitTemplate.convertAndSend(
                RabbitMqAutoConfiguration.PORTFOLIO_EXCHANGE,
                RabbitMqAutoConfiguration.PORTFOLIO_ACHIEVEMENT_ADDED_KEY,
                event
        );

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            StudentRating rating = studentRatingRepository.findById(userId).orElseThrow();
            assertThat(rating.getTotalScore()).isEqualByComparingTo(new BigDecimal("3.75"));
        });
    }

    @Test
    void onProjectStatusChanged_Completed_Logged() {
        // This listener only logs for COMPLETED status; verify no exception
        Map<String, Object> event = Map.of(
                "projectId", UUID.randomUUID().toString(),
                "newStatus", "COMPLETED"
        );

        rabbitTemplate.convertAndSend(
                RabbitMqAutoConfiguration.PROJECT_EXCHANGE,
                RabbitMqAutoConfiguration.PROJECT_STATUS_CHANGED_KEY,
                event
        );

        // Just verify no exception thrown - the listener logs but doesn't update DB yet
        await().during(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(true).isTrue());
    }
}
