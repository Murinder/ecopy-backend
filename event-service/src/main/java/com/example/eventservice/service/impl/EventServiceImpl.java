package com.example.eventservice.service.impl;

import com.example.eventservice.exception.ResourceNotFoundException;
import com.example.eventservice.dto.CreateEventDto;
import com.example.eventservice.dto.UpdateEventDto;
import com.example.eventservice.mapper.EventMapper;
import com.example.eventservice.model.Event;
import com.example.eventservice.repository.EventRepository;
import com.example.eventservice.service.EventService;
import com.example.sharedlib.config.RabbitMqAutoConfiguration;
import com.example.sharedlib.exception.ForbiddenException;
import com.example.sharedlib.security.UserContext;
import com.example.sharedlib.dto.EventDto;
import com.example.sharedlib.event.EventEvent;
import com.example.sharedlib.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<EventDto> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(eventMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EventDto getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        return eventMapper.toDto(event);
    }

    @Override
    @Transactional
    public EventDto createEvent(CreateEventDto createEventDto) {
        if (createEventDto.getStartDate() != null && createEventDto.getEndDate() != null
                && !createEventDto.getEndDate().isAfter(createEventDto.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        Event event = eventMapper.toEntity(createEventDto);
        event.setCreatedBy(UserContext.getCurrentUserId());
        if (event.getStatus() == null) {
            event.setStatus(com.example.sharedlib.enums.EventStatus.DRAFT);
        }
        event = eventRepository.save(event);

        EventEvent domainEvent = EventEvent.builder()
                .domainEventId(event.getId())
                .title(event.getTitle())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .build();
        domainEvent.init("event-service");
        eventPublisher.publish(RabbitMqAutoConfiguration.EVENT_EXCHANGE, "event.created", domainEvent);

        return eventMapper.toDto(event);
    }

    @Override
    @Transactional
    public EventDto updateEvent(UUID id, UpdateEventDto updateEventDto) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        String oldStatus = event.getStatus().name();
        eventMapper.updateEntity(updateEventDto, event);
        event.setUpdatedAt(OffsetDateTime.now());
        event = eventRepository.save(event);

        if (!oldStatus.equals(event.getStatus().name())) {
            EventEvent domainEvent = EventEvent.builder()
                    .domainEventId(event.getId())
                    .oldStatus(oldStatus)
                    .newStatus(event.getStatus().name())
                    .build();
            domainEvent.init("event-service");
            eventPublisher.publish(RabbitMqAutoConfiguration.EVENT_EXCHANGE, "event.status_changed", domainEvent);
        }

        return eventMapper.toDto(event);
    }

    @Override
    @Transactional
    public void deleteEvent(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!UserContext.hasRole("ADMIN")) {
            UUID callerId = UserContext.getCurrentUserId();
            if (!event.getCreatedBy().equals(callerId)) {
                throw new ForbiddenException("You can only delete your own events");
            }
        }
        eventRepository.delete(event);
    }
}
