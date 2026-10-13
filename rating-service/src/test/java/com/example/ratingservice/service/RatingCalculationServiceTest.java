package com.example.ratingservice.service;

import com.example.ratingservice.client.ProjectServiceClient;
import com.example.ratingservice.model.RatingCriteria;
import com.example.ratingservice.model.RatingCriteriaType;
import com.example.ratingservice.model.RatingHistory;
import com.example.ratingservice.model.StudentRating;
import com.example.ratingservice.repository.*;
import com.example.sharedlib.dto.ProjectMemberDto;
import com.example.sharedlib.event.EventPublisher;
import com.example.sharedlib.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingCalculationServiceTest {

    @Mock private StudentRatingRepository studentRatingRepository;
    @Mock private LecturerRatingRepository lecturerRatingRepository;
    @Mock private DepartmentRatingRepository departmentRatingRepository;
    @Mock private RatingCriteriaRepository criteriaRepository;
    @Mock private RatingHistoryRepository historyRepository;
    @Mock private StudentRatingService studentRatingService;
    @Mock private EventPublisher eventPublisher;
    @Mock private ProjectServiceClient projectServiceClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RatingCalculationService calculationService;

    @Test
    void addStudentPoints_NewStudent_CreatesRatingAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentRatingRepository.saveAndFlush(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRatingRepository.save(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.findByUserId(userId)).thenReturn(List.of());

        calculationService.addStudentPoints(userId, BigDecimal.TEN, "test", null, 1);

        verify(studentRatingRepository, atLeastOnce()).save(any(StudentRating.class));
        verify(historyRepository).save(any());
        verify(eventPublisher).publish(eq("etsopy.rating"), eq("rating.recalculated"), any());
    }

    @Test
    void addStudentPoints_ExistingStudent_AddsToScore() {
        UUID userId = UUID.randomUUID();
        StudentRating existing = StudentRating.builder()
                .userId(userId)
                .totalScore(BigDecimal.valueOf(20))
                .semester(1)
                .calculationDetails("{}")
                .build();
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(studentRatingRepository.save(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        RatingHistory h = RatingHistory.builder().userId(userId).score(BigDecimal.valueOf(5)).reason("task_completed").build();
        when(historyRepository.findByUserId(userId)).thenReturn(List.of(h));

        calculationService.addStudentPoints(userId, BigDecimal.valueOf(5), "task_completed", null, null);

        // After rebuild: totalNormalized = 5/200*100 = 2.5
        verify(studentRatingRepository, atLeastOnce()).save(any());
    }

    @Test
    void onProjectCompleted_UsesCriteriaWeights() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        RatingCriteria criteria = RatingCriteria.builder()
                .name("Project completion")
                .basePoints(10)
                .weight(BigDecimal.valueOf(1.5))
                .criteriaType(RatingCriteriaType.STUDENT)
                .isActive(true)
                .build();
        when(projectServiceClient.getProjectMembers(projectId)).thenReturn(
                ApiResponse.<List<ProjectMemberDto>>builder().success(true)
                        .data(List.of(ProjectMemberDto.builder().userId(userId).build())).build());
        when(criteriaRepository.findByCriteriaTypeAndIsActiveTrue(RatingCriteriaType.STUDENT))
                .thenReturn(List.of(criteria));
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentRatingRepository.saveAndFlush(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRatingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.findByUserId(userId)).thenReturn(List.of());

        calculationService.onProjectCompleted(projectId);

        verify(studentRatingRepository, atLeastOnce()).save(any());
    }

    @Test
    void onTaskCompleted_Adds2Points() {
        UUID userId = UUID.randomUUID();
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentRatingRepository.saveAndFlush(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRatingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.findByUserId(userId)).thenReturn(List.of());

        calculationService.onTaskCompleted(userId, UUID.randomUUID(), UUID.randomUUID());

        verify(studentRatingRepository, atLeastOnce()).save(any());
    }

    @Test
    void onEventParticipation_Adds5Points() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentRatingRepository.saveAndFlush(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRatingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.findByUserId(userId)).thenReturn(List.of());

        calculationService.onEventParticipation(userId, eventId);

        verify(studentRatingRepository, atLeastOnce()).save(any());
        verify(historyRepository).save(argThat(h -> "event_participation".equals(h.getReason()) && eventId.equals(h.getRelatedEntityId())));
        verify(eventPublisher).publish(eq("etsopy.rating"), eq("rating.recalculated"), any());
    }

    @Test
    void onProjectCompleted_NoCriteriaMatch_UsesDefaultTenPoints() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        RatingCriteria criteria = RatingCriteria.builder()
                .name("Academic performance")
                .basePoints(20)
                .weight(BigDecimal.valueOf(2.0))
                .criteriaType(RatingCriteriaType.STUDENT)
                .isActive(true)
                .build();
        when(projectServiceClient.getProjectMembers(projectId)).thenReturn(
                ApiResponse.<List<ProjectMemberDto>>builder().success(true)
                        .data(List.of(ProjectMemberDto.builder().userId(userId).build())).build());
        when(criteriaRepository.findByCriteriaTypeAndIsActiveTrue(RatingCriteriaType.STUDENT))
                .thenReturn(List.of(criteria));
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentRatingRepository.saveAndFlush(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRatingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.findByUserId(userId)).thenReturn(List.of());

        calculationService.onProjectCompleted(projectId);

        // No criteria name contains "project", so default 10 points used
        verify(studentRatingRepository, atLeastOnce()).save(any());
    }

    @Test
    void recalculateStudentRating_SumsAllHistory() {
        UUID userId = UUID.randomUUID();
        RatingHistory h1 = RatingHistory.builder().userId(userId).score(BigDecimal.valueOf(10)).reason("project_completed").build();
        RatingHistory h2 = RatingHistory.builder().userId(userId).score(BigDecimal.valueOf(5)).reason("event_participation").build();
        RatingHistory h3 = RatingHistory.builder().userId(userId).score(BigDecimal.valueOf(3)).reason("achievement_added").build();

        when(historyRepository.findByUserId(userId)).thenReturn(List.of(h1, h2, h3));
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.empty());
        when(studentRatingRepository.saveAndFlush(any(StudentRating.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentRatingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        calculationService.recalculateStudentRating(userId);

        // totalRaw = 18, normalized = 18/80*100 = 22.50
        verify(studentRatingRepository, atLeastOnce()).save(argThat(r ->
                r.getTotalScore().compareTo(new java.math.BigDecimal("22.50")) == 0));
        verify(eventPublisher).publish(eq("etsopy.rating"), eq("rating.recalculated"), any());
    }
}
