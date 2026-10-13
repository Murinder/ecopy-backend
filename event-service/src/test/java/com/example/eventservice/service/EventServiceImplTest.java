package com.example.eventservice.service;

import com.example.eventservice.dto.CreateEventDto;
import com.example.eventservice.dto.UpdateEventDto;
import com.example.eventservice.exception.ResourceNotFoundException;
import com.example.eventservice.mapper.EventMapper;
import com.example.eventservice.model.Event;
import com.example.eventservice.repository.EventRepository;
import com.example.eventservice.service.impl.EventServiceImpl;
import com.example.sharedlib.dto.EventDto;
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
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private EventServiceImpl eventService;

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

    private Event buildEvent(UUID id, EventStatus status) {
        return Event.builder()
                .id(id)
                .title("Hackathon")
                .startDate(OffsetDateTime.now())
                .endDate(OffsetDateTime.now().plusDays(2))
                .format(EventFormat.ONLINE)
                .status(status)
                .createdBy(UUID.randomUUID())
                .build();
    }

    @Test
    void createEvent_PublishesCreatedEvent() {
        CreateEventDto createDto = new CreateEventDto();
        Event event = buildEvent(UUID.randomUUID(), EventStatus.DRAFT);
        EventDto eventDto = new EventDto();

        when(eventMapper.toEntity(createDto)).thenReturn(event);
        when(eventRepository.save(event)).thenReturn(event);
        when(eventMapper.toDto(event)).thenReturn(eventDto);

        EventDto result = eventService.createEvent(createDto);

        assertNotNull(result);
        verify(eventPublisher).publish(eq("etsopy.event"), eq("event.created"), any());
    }

    @Test
    void getEventById_ReturnsDto_WhenFound() {
        UUID eventId = UUID.randomUUID();
        Event event = buildEvent(eventId, EventStatus.DRAFT);
        EventDto eventDto = new EventDto();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventMapper.toDto(event)).thenReturn(eventDto);

        EventDto result = eventService.getEventById(eventId);

        assertNotNull(result);
        verify(eventRepository).findById(eventId);
    }

    @Test
    void getEventById_ThrowsResourceNotFoundException_WhenNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.getEventById(eventId));
    }

    @Test
    void getAllEvents_ReturnsListOfDtos() {
        Event event = buildEvent(UUID.randomUUID(), EventStatus.DRAFT);
        EventDto dto = new EventDto();
        when(eventRepository.findAll()).thenReturn(List.of(event));
        when(eventMapper.toDto(event)).thenReturn(dto);

        List<EventDto> result = eventService.getAllEvents();

        assertEquals(1, result.size());
        verify(eventRepository).findAll();
    }

    @Test
    void getAllEvents_ReturnsEmptyList_WhenNoEvents() {
        when(eventRepository.findAll()).thenReturn(Collections.emptyList());

        List<EventDto> result = eventService.getAllEvents();

        assertTrue(result.isEmpty());
    }

    @Test
    void updateEvent_PublishesStatusChanged_WhenStatusDiffers() {
        UUID eventId = UUID.randomUUID();
        Event event = buildEvent(eventId, EventStatus.DRAFT);
        UpdateEventDto updateDto = UpdateEventDto.builder().status(EventStatus.PUBLISHED).build();
        EventDto dto = new EventDto();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        // After updateEntity is called, the event status changes
        doAnswer(invocation -> {
            Event target = invocation.getArgument(1);
            target.setStatus(EventStatus.PUBLISHED);
            return null;
        }).when(eventMapper).updateEntity(eq(updateDto), eq(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(eventMapper.toDto(event)).thenReturn(dto);

        EventDto result = eventService.updateEvent(eventId, updateDto);

        assertNotNull(result);
        verify(eventPublisher).publish(eq("etsopy.event"), eq("event.status_changed"), any());
    }

    @Test
    void updateEvent_DoesNotPublish_WhenStatusUnchanged() {
        UUID eventId = UUID.randomUUID();
        Event event = buildEvent(eventId, EventStatus.DRAFT);
        UpdateEventDto updateDto = UpdateEventDto.builder().title("New Title").build();
        EventDto dto = new EventDto();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        // updateEntity does not change status
        when(eventRepository.save(event)).thenReturn(event);
        when(eventMapper.toDto(event)).thenReturn(dto);

        EventDto result = eventService.updateEvent(eventId, updateDto);

        assertNotNull(result);
        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void deleteEvent_CallsRepositoryDeleteById() {
        UUID eventId = UUID.randomUUID();
        Event event = buildEvent(eventId, EventStatus.DRAFT);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        eventService.deleteEvent(eventId);

        verify(eventRepository).delete(event);
    }
}
