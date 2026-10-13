package com.example.eventservice.service;

import com.example.eventservice.dto.CreateEventApplicationDto;
import com.example.eventservice.dto.EventApplicationDto;
import com.example.eventservice.dto.UpdateEventApplicationDto;
import com.example.eventservice.exception.ResourceNotFoundException;
import com.example.eventservice.mapper.EventApplicationMapper;
import com.example.eventservice.model.Event;
import com.example.eventservice.model.EventApplication;
import com.example.eventservice.model.enums.ApplicationStatus;
import com.example.eventservice.repository.EventApplicationRepository;
import com.example.eventservice.repository.EventRepository;
import com.example.eventservice.repository.TeamMemberRepository;
import com.example.eventservice.repository.TeamRepository;
import com.example.eventservice.service.impl.EventApplicationServiceImpl;
import com.example.sharedlib.enums.EventFormat;
import com.example.sharedlib.enums.EventStatus;
import com.example.sharedlib.event.EventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventApplicationServiceImplTest {

    @Mock
    private EventApplicationRepository applicationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private EventApplicationMapper applicationMapper;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private UserNameCacheService userNameCacheService;

    @InjectMocks
    private EventApplicationServiceImpl applicationService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID().toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Event buildEvent() {
        return Event.builder()
                .id(UUID.randomUUID())
                .title("Hackathon")
                .startDate(OffsetDateTime.now())
                .endDate(OffsetDateTime.now().plusDays(2))
                .format(EventFormat.ONLINE)
                .status(EventStatus.DRAFT)
                .createdBy(UUID.randomUUID())
                .build();
    }

    private EventApplication buildApplication(UUID id, Event event, UUID userId) {
        return EventApplication.builder()
                .id(id)
                .event(event)
                .userId(userId)
                .status(ApplicationStatus.SUBMITTED)
                .build();
    }

    @Test
    void createApplication_PublishesApplicationSubmittedEvent() {
        Event event = buildEvent();
        UUID userId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        CreateEventApplicationDto createDto = CreateEventApplicationDto.builder()
                .eventId(event.getId())
                .userId(userId)
                .build();
        EventApplication application = buildApplication(appId, event, userId);
        EventApplicationDto dto = EventApplicationDto.builder()
                .id(appId)
                .eventId(event.getId())
                .userId(userId)
                .status("SUBMITTED")
                .build();

        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(applicationMapper.toEntity(createDto)).thenReturn(application);
        when(applicationRepository.save(application)).thenReturn(application);
        when(applicationMapper.toDto(application)).thenReturn(dto);

        EventApplicationDto result = applicationService.createApplication(createDto);

        assertNotNull(result);
        assertEquals(appId, result.getId());
        verify(eventPublisher).publish(eq("etsopy.event"), eq("event.application_submitted"), any());
    }

    @Test
    void createApplication_ThrowsResourceNotFoundException_WhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        CreateEventApplicationDto createDto = CreateEventApplicationDto.builder()
                .eventId(eventId)
                .userId(UUID.randomUUID())
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> applicationService.createApplication(createDto));
        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void updateApplication_PublishesApplicationDecidedEvent() {
        Event event = buildEvent();
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EventApplication application = buildApplication(appId, event, userId);
        UpdateEventApplicationDto updateDto = UpdateEventApplicationDto.builder()
                .status(ApplicationStatus.APPROVED)
                .build();
        EventApplicationDto dto = EventApplicationDto.builder()
                .id(appId)
                .eventId(event.getId())
                .userId(userId)
                .status("APPROVED")
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        doAnswer(invocation -> {
            EventApplication target = invocation.getArgument(1);
            target.setStatus(ApplicationStatus.APPROVED);
            return null;
        }).when(applicationMapper).updateEntity(eq(updateDto), eq(application));
        when(applicationRepository.save(application)).thenReturn(application);
        when(applicationMapper.toDto(application)).thenReturn(dto);

        EventApplicationDto result = applicationService.updateApplication(appId, updateDto);

        assertNotNull(result);
        verify(eventPublisher).publish(eq("etsopy.event"), eq("event.application_decided"), any());
    }

    @Test
    void getApplicationById_ReturnsDto_WhenFound() {
        Event event = buildEvent();
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EventApplication application = buildApplication(appId, event, userId);
        EventApplicationDto dto = new EventApplicationDto();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(applicationMapper.toDto(application)).thenReturn(dto);

        EventApplicationDto result = applicationService.getApplicationById(appId);

        assertNotNull(result);
        verify(applicationRepository).findById(appId);
    }

    @Test
    void getApplicationById_ThrowsResourceNotFoundException_WhenNotFound() {
        UUID appId = UUID.randomUUID();
        when(applicationRepository.findById(appId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> applicationService.getApplicationById(appId));
    }

    @Test
    void getAllApplications_ReturnsEmptyList() {
        when(applicationRepository.findAll()).thenReturn(Collections.emptyList());

        List<EventApplicationDto> result = applicationService.getAllApplications();

        assertTrue(result.isEmpty());
    }

    @Test
    void deleteApplication_CallsRepositoryDeleteById() {
        UUID appId = UUID.randomUUID();
        EventApplication application = buildApplication(appId, buildEvent(), UUID.randomUUID());
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        applicationService.deleteApplication(appId);

        verify(applicationRepository).deleteById(appId);
    }
}
