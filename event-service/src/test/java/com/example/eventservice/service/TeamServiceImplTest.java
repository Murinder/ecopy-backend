package com.example.eventservice.service;

import com.example.eventservice.dto.CreateTeamDto;
import com.example.eventservice.dto.TeamDto;
import com.example.eventservice.dto.UpdateTeamDto;
import com.example.eventservice.exception.ResourceNotFoundException;
import com.example.eventservice.mapper.TeamMapper;
import com.example.eventservice.model.Event;
import com.example.eventservice.model.Team;
import com.example.eventservice.repository.EventRepository;
import com.example.eventservice.repository.TeamMemberRepository;
import com.example.eventservice.repository.TeamRepository;
import com.example.eventservice.service.impl.TeamServiceImpl;
import com.example.sharedlib.enums.EventFormat;
import com.example.sharedlib.enums.EventStatus;
import com.example.sharedlib.event.EventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class TeamServiceImplTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private UserNameCacheService userNameCacheService;

    @InjectMocks
    private TeamServiceImpl teamService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID().toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
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

    private Team buildTeam(UUID id, Event event) {
        return Team.builder()
                .id(id)
                .event(event)
                .name("Alpha Team")
                .createdBy(UUID.randomUUID())
                .build();
    }

    @Test
    void createTeam_PublishesTeamFormedEvent() {
        Event event = buildEvent();
        UUID teamId = UUID.randomUUID();
        CreateTeamDto createDto = CreateTeamDto.builder()
                .eventId(event.getId())
                .name("Alpha Team")
                .build();
        Team team = buildTeam(teamId, event);
        TeamDto dto = TeamDto.builder()
                .id(teamId)
                .eventId(event.getId())
                .name("Alpha Team")
                .build();

        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(teamMapper.toEntity(createDto)).thenReturn(team);
        when(teamRepository.save(team)).thenReturn(team);
        when(teamMapper.toDto(team)).thenReturn(dto);

        TeamDto result = teamService.createTeam(createDto);

        assertNotNull(result);
        assertEquals("Alpha Team", result.getName());
        verify(eventPublisher).publish(eq("etsopy.event"), eq("event.team_formed"), any());
    }

    @Test
    void createTeam_ThrowsResourceNotFoundException_WhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        CreateTeamDto createDto = CreateTeamDto.builder()
                .eventId(eventId)
                .name("Team X")
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.createTeam(createDto));
        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void getTeamById_ReturnsDto_WhenFound() {
        Event event = buildEvent();
        UUID teamId = UUID.randomUUID();
        Team team = buildTeam(teamId, event);
        TeamDto dto = new TeamDto();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMapper.toDto(team)).thenReturn(dto);

        TeamDto result = teamService.getTeamById(teamId);

        assertNotNull(result);
        verify(teamRepository).findById(teamId);
    }

    @Test
    void getTeamById_ThrowsResourceNotFoundException_WhenNotFound() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamService.getTeamById(teamId));
    }

    @Test
    void getAllTeams_ReturnsEmptyList() {
        when(teamRepository.findAll()).thenReturn(Collections.emptyList());

        List<TeamDto> result = teamService.getAllTeams();

        assertTrue(result.isEmpty());
    }

    @Test
    void updateTeam_ReturnsUpdatedDto() {
        Event event = buildEvent();
        UUID teamId = UUID.randomUUID();
        Team team = buildTeam(teamId, event);
        UpdateTeamDto updateDto = UpdateTeamDto.builder().name("Beta Team").build();
        TeamDto dto = TeamDto.builder().id(teamId).name("Beta Team").build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.save(team)).thenReturn(team);
        when(teamMapper.toDto(team)).thenReturn(dto);

        TeamDto result = teamService.updateTeam(teamId, updateDto);

        assertNotNull(result);
        assertEquals("Beta Team", result.getName());
        // updateTeam does NOT publish events
        verify(eventPublisher, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void deleteTeam_CallsRepositoryDeleteById() {
        UUID teamId = UUID.randomUUID();

        teamService.deleteTeam(teamId);

        verify(teamRepository).deleteById(teamId);
    }
}
