package com.example.ratingservice.service;

import com.example.ratingservice.dto.RatingHistoryDto;
import com.example.ratingservice.dto.StudentRatingDto;
import com.example.ratingservice.client.CoreServiceClient;
import com.example.ratingservice.model.RatingHistory;
import com.example.ratingservice.model.StudentRating;
import com.example.ratingservice.repository.EarnedAchievementRepository;
import com.example.ratingservice.repository.RatingHistoryRepository;
import com.example.ratingservice.repository.StudentRatingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRatingServiceTest {

    @Mock
    private StudentRatingRepository studentRatingRepository;

    @Mock
    private RatingHistoryRepository historyRepository;

    @Mock
    private EarnedAchievementRepository earnedAchievementRepository;

    @Mock
    private CoreServiceClient coreServiceClient;

    @Mock
    private UserNameCacheService userNameCacheService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StudentRatingService studentRatingService;

    @Test
    void getStudentRating_ExistingUser_ReturnsDto() {
        UUID userId = UUID.randomUUID();
        StudentRating rating = StudentRating.builder()
                .userId(userId)
                .totalScore(BigDecimal.valueOf(42))
                .calculationDetails("{\"projects\":3}")
                .semester(2)
                .verificationStatus(StudentRating.VerificationStatus.VERIFIED)
                .updatedAt(OffsetDateTime.now())
                .build();

        when(studentRatingRepository.findById(userId)).thenReturn(Optional.of(rating));

        StudentRatingDto result = studentRatingService.getStudentRating(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.valueOf(42), result.getTotalScore());
        assertEquals(2, result.getSemester());
        assertEquals("VERIFIED", result.getVerificationStatus());
        assertEquals("{\"projects\":3}", result.getCalculationDetails());
    }

    @Test
    void getStudentRating_NonExistingUser_ReturnsDefaultRating() {
        UUID userId = UUID.randomUUID();
        when(studentRatingRepository.findById(userId)).thenReturn(Optional.empty());

        StudentRatingDto result = studentRatingService.getStudentRating(userId);

        assertEquals(userId, result.getUserId());
        assertEquals(BigDecimal.ZERO, result.getTotalScore());
        assertEquals(1, result.getSemester());
        assertEquals("PENDING", result.getVerificationStatus());
    }

    @Test
    void getTopStudents_ReturnsOrderedList() {
        StudentRating r1 = StudentRating.builder()
                .userId(UUID.randomUUID())
                .totalScore(BigDecimal.valueOf(100))
                .semester(1)
                .calculationDetails("{}")
                .build();
        StudentRating r2 = StudentRating.builder()
                .userId(UUID.randomUUID())
                .totalScore(BigDecimal.valueOf(80))
                .semester(1)
                .calculationDetails("{}")
                .build();

        when(studentRatingRepository.findTopStudents(PageRequest.of(0, 5))).thenReturn(List.of(r1, r2));

        List<StudentRatingDto> result = studentRatingService.getTopStudents(5);

        assertEquals(2, result.size());
        assertEquals(BigDecimal.valueOf(100), result.get(0).getTotalScore());
        assertEquals(BigDecimal.valueOf(80), result.get(1).getTotalScore());
    }

    @Test
    void getTopStudents_EmptyList_ReturnsEmpty() {
        when(studentRatingRepository.findTopStudents(PageRequest.of(0, 3))).thenReturn(List.of());

        List<StudentRatingDto> result = studentRatingService.getTopStudents(3);

        assertTrue(result.isEmpty());
    }

//    @Test
//    void getRatingDetails_ExistingUser_ReturnsHistoryList() {
//        UUID userId = UUID.randomUUID();
//        RatingHistory h = RatingHistory.builder()
//                .id(UUID.randomUUID())
//                .userId(userId)
//                .score(BigDecimal.valueOf(5))
//                .reason("task_completed")
//                .semester(3)
//                .calculatedAt(OffsetDateTime.now())
//                .build();
//
//        when(historyRepository.findByUserIdOrderByCalculatedAtDesc(userId)).thenReturn(List.of(h));
//
//        List<RatingHistoryDto> result = studentRatingService.getRatingDetails(userId);
//
//        assertEquals(1, result.size());
//        assertEquals(userId, result.get(0).getUserId());
//        assertEquals("task_completed", result.get(0).getReason());
//    }
//
//    @Test
//    void getRatingDetails_NoHistory_ReturnsEmptyList() {
//        UUID userId = UUID.randomUUID();
//        when(historyRepository.findByUserIdOrderByCalculatedAtDesc(userId)).thenReturn(List.of());
//
//        List<RatingHistoryDto> result = studentRatingService.getRatingDetails(userId);
//
//        assertTrue(result.isEmpty());
//    }
}
