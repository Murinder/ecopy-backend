package com.example.projectservice.service;

import com.example.projectservice.model.Project;
import com.example.projectservice.repository.ProjectMemberRepository;
import com.example.projectservice.repository.ProjectRepository;
import com.example.projectservice.repository.ProjectTemplateRepository;
import com.example.projectservice.repository.TaskRepository;
import com.example.sharedlib.dto.ProjectDto;
import com.example.sharedlib.enums.ProjectRole;
import com.example.sharedlib.enums.ProjectStatus;
import com.example.sharedlib.event.EventPublisher;
import com.example.sharedlib.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ProjectTemplateRepository projectTemplateRepository;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createProject_PublishesEvent() {
        ProjectDto dto = ProjectDto.builder()
                .title("Test Project")
                .description("Description")
                .createdBy(UUID.randomUUID())
                .build();

        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            return p;
        });

        ProjectDto result = projectService.createProject(dto);

        assertNotNull(result);
        assertEquals("Test Project", result.getTitle());
        verify(projectMemberService).addMember(any(Project.class), any(UUID.class), eq(ProjectRole.LEADER));
        verify(eventPublisher).publish(eq("etsopy.project"), eq("project.created"), any());
    }

    @Test
    void changeProjectStatus_PublishesEvent() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .title("Test")
                .status(ProjectStatus.ACTIVE)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectDto result = projectService.changeProjectStatus(projectId, ProjectStatus.COMPLETED);

        assertEquals(ProjectStatus.COMPLETED, result.getStatus());
        verify(eventPublisher).publish(eq("etsopy.project"), eq("project.status_changed"), any());
    }

    @Test
    void getProjectById_WhenNotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.getProjectById(id));
    }

    @Test
    void updateProject_UpdatesTitleAndDescription() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .title("Old Title")
                .description("Old Description")
                .status(ProjectStatus.ACTIVE)
                .createdBy(UUID.randomUUID())
                .build();

        ProjectDto updateDto = ProjectDto.builder()
                .title("New Title")
                .description("New Description")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectDto result = projectService.updateProject(projectId, updateDto);

        assertEquals("New Title", result.getTitle());
        assertEquals("New Description", result.getDescription());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void updateProject_WhenNotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        ProjectDto updateDto = ProjectDto.builder().title("New").build();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.updateProject(id, updateDto));
    }

    @Test
    void deleteProject_CallsRepositoryDeleteById() {
        UUID projectId = UUID.randomUUID();

        projectService.deleteProject(projectId);

        verify(projectRepository).deleteById(projectId);
    }

    @Test
    void getProjectById_WhenFound_ReturnsDto() {
        UUID projectId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .title("Test Project")
                .description("Desc")
                .status(ProjectStatus.ACTIVE)
                .createdBy(createdBy)
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ProjectDto result = projectService.getProjectById(projectId);

        assertNotNull(result);
        assertEquals(projectId, result.getId());
        assertEquals("Test Project", result.getTitle());
        assertEquals(createdBy, result.getCreatedBy());
    }

    @Test
    void getDepartmentProjects_ReturnsList() {
        UUID departmentId = UUID.randomUUID();
        Project project = Project.builder()
                .id(UUID.randomUUID())
                .title("Dept Project")
                .status(ProjectStatus.ACTIVE)
                .createdBy(UUID.randomUUID())
                .departmentId(departmentId)
                .build();

        when(projectRepository.findByStatusAndDepartmentId(ProjectStatus.ACTIVE, departmentId))
                .thenReturn(List.of(project));

        List<ProjectDto> result = projectService.getDepartmentProjects(departmentId);

        assertEquals(1, result.size());
        assertEquals("Dept Project", result.get(0).getTitle());
    }
}
