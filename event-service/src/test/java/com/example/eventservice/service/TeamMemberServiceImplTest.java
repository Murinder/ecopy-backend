package com.example.eventservice.service;

import com.example.eventservice.dto.CreateTeamMemberDto;
import com.example.eventservice.dto.TeamMemberDto;
import com.example.eventservice.dto.UpdateTeamMemberDto;
import com.example.eventservice.exception.ResourceNotFoundException;
import com.example.eventservice.mapper.TeamMemberMapper;
import com.example.eventservice.model.Event;
import com.example.eventservice.model.Team;
import com.example.eventservice.model.TeamMember;
import com.example.eventservice.repository.EventApplicationRepository;
import com.example.eventservice.repository.TeamMemberRepository;
import com.example.eventservice.repository.TeamRepository;
import com.example.eventservice.service.impl.TeamMemberServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceImplTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberMapper teamMemberMapper;
    @Mock
    private EventApplicationRepository applicationRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private TeamMemberServiceImpl teamMemberService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private TeamMember.TeamMemberId buildCompositeId(UUID teamId, UUID userId) {
        return new TeamMember.TeamMemberId(teamId, userId);
    }

    @Test
    void createTeamMember_ReturnsDtoAndSaves() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateTeamMemberDto createDto = CreateTeamMemberDto.builder()
                .teamId(teamId)
                .userId(userId)
                .role("member")
                .build();
        Team team = Team.builder()
                .id(teamId)
                .event(Event.builder().id(UUID.randomUUID()).build())
                .name("Alpha")
                .createdBy(UUID.randomUUID())
                .build();
        TeamMember member = TeamMember.builder()
                .id(buildCompositeId(teamId, userId))
                .team(team)
                .role("member")
                .build();
        TeamMemberDto dto = TeamMemberDto.builder()
                .teamId(teamId)
                .userId(userId)
                .role("member")
                .build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberMapper.toEntity(createDto)).thenReturn(member);
        when(teamMemberRepository.save(member)).thenReturn(member);
        when(teamMemberMapper.toDto(member)).thenReturn(dto);

        TeamMemberDto result = teamMemberService.createTeamMember(createDto);

        assertNotNull(result);
        assertEquals(teamId, result.getTeamId());
        assertEquals(userId, result.getUserId());
        verify(teamMemberRepository).save(member);
    }

    @Test
    void createTeamMember_ThrowsResourceNotFoundException_WhenTeamNotFound() {
        UUID teamId = UUID.randomUUID();
        CreateTeamMemberDto createDto = CreateTeamMemberDto.builder()
                .teamId(teamId)
                .userId(UUID.randomUUID())
                .role("member")
                .build();

        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamMemberService.createTeamMember(createDto));
    }

    @Test
    void getTeamMemberById_ReturnsDto_WhenFound() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMember.TeamMemberId compositeId = buildCompositeId(teamId, userId);
        TeamMember member = TeamMember.builder()
                .id(compositeId)
                .role("member")
                .build();
        TeamMemberDto dto = TeamMemberDto.builder()
                .teamId(teamId)
                .userId(userId)
                .role("member")
                .build();

        when(teamMemberRepository.findById(compositeId)).thenReturn(Optional.of(member));
        when(teamMemberMapper.toDto(member)).thenReturn(dto);

        TeamMemberDto result = teamMemberService.getTeamMemberById(compositeId);

        assertNotNull(result);
        assertEquals("member", result.getRole());
    }

    @Test
    void getTeamMemberById_ThrowsResourceNotFoundException_WhenNotFound() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMember.TeamMemberId compositeId = buildCompositeId(teamId, userId);

        when(teamMemberRepository.findById(compositeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamMemberService.getTeamMemberById(compositeId));
    }

    @Test
    void updateTeamMember_ReturnsUpdatedDto() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMember.TeamMemberId compositeId = buildCompositeId(teamId, userId);
        TeamMember member = TeamMember.builder()
                .id(compositeId)
                .role("member")
                .build();
        UpdateTeamMemberDto updateDto = UpdateTeamMemberDto.builder().role("lead").build();
        TeamMemberDto dto = TeamMemberDto.builder()
                .teamId(teamId)
                .userId(userId)
                .role("lead")
                .build();

        when(teamMemberRepository.findById(compositeId)).thenReturn(Optional.of(member));
        when(teamMemberRepository.save(member)).thenReturn(member);
        when(teamMemberMapper.toDto(member)).thenReturn(dto);

        TeamMemberDto result = teamMemberService.updateTeamMember(compositeId, updateDto);

        assertNotNull(result);
        assertEquals("lead", result.getRole());
    }

    @Test
    void deleteTeamMember_CallsRepositoryDeleteById() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        TeamMember.TeamMemberId compositeId = buildCompositeId(teamId, userId);
        Event event = Event.builder().id(UUID.randomUUID()).title("Hackathon").build();
        Team team = Team.builder().id(teamId).event(event).name("Alpha").createdBy(leaderId).build();
        TeamMember member = TeamMember.builder().id(compositeId).team(team).build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        leaderId.toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        when(teamMemberRepository.findById(compositeId)).thenReturn(Optional.of(member));

        teamMemberService.deleteTeamMember(compositeId);

        verify(teamMemberRepository).delete(member);
    }

    @Test
    void deleteTeamMember_ThrowsResourceNotFoundException_WhenNotFound() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMember.TeamMemberId compositeId = buildCompositeId(teamId, userId);

        when(teamMemberRepository.findById(compositeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> teamMemberService.deleteTeamMember(compositeId));
    }

    @Test
    void getAllTeamMembers_ReturnsEmptyList() {
        when(teamMemberRepository.findAll()).thenReturn(Collections.emptyList());

        List<TeamMemberDto> result = teamMemberService.getAllTeamMembers();

        assertTrue(result.isEmpty());
    }
}
