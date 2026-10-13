package com.example.projectservice.service;

import com.example.projectservice.model.Project;
import com.example.projectservice.model.Task;
import com.example.projectservice.repository.ProjectMemberRepository;
import com.example.projectservice.repository.TaskRepository;
import com.example.sharedlib.dto.TaskDto;
import com.example.sharedlib.enums.TaskStatus;
import com.example.sharedlib.event.EventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private UserNameCacheService userNameCacheService;

    @InjectMocks
    private TaskService taskService;

    @Test
    void changeTaskStatus_ToDone_PublishesTaskCompletedEvent() {
        UUID taskId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(taskId)
                .project(project)
                .title("Test task")
                .status(TaskStatus.IN_PROGRESS)
                .assignedTo(UUID.randomUUID())
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        TaskDto result = taskService.changeTaskStatus(taskId, TaskStatus.DONE);

        assertEquals(TaskStatus.DONE, result.getStatus());
        verify(eventPublisher).publish(eq("etsopy.project"), eq("project.task_completed"), any());
    }

    @Test
    void changeTaskStatus_ToInProgress_DoesNotPublishCompletedEvent() {
        UUID taskId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(taskId)
                .project(project)
                .title("Test task")
                .status(TaskStatus.TO_DO)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenReturn(task);

        taskService.changeTaskStatus(taskId, TaskStatus.IN_PROGRESS);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void updateTask_UpdatesFieldsCorrectly() {
        UUID taskId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(taskId)
                .project(project)
                .title("Old Title")
                .description("Old Desc")
                .status(TaskStatus.TO_DO)
                .build();

        TaskDto updateDto = TaskDto.builder()
                .title("New Title")
                .description("New Desc")
                .dueDate(LocalDate.of(2026, 6, 15))
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskDto result = taskService.updateTask(taskId, updateDto);

        assertEquals("New Title", result.getTitle());
        assertEquals("New Desc", result.getDescription());
        assertEquals(LocalDate.of(2026, 6, 15), result.getDueDate());
    }

    @Test
    void deleteTask_CallsRepositoryDeleteById() {
        UUID taskId = UUID.randomUUID();

        taskService.deleteTask(taskId);

        verify(taskRepository).deleteById(taskId);
    }

    @Test
    void getProjectTasks_ReturnsList() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Task task1 = Task.builder().id(UUID.randomUUID()).project(project).title("Task 1").status(TaskStatus.TO_DO).build();
        Task task2 = Task.builder().id(UUID.randomUUID()).project(project).title("Task 2").status(TaskStatus.IN_PROGRESS).build();

        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(task1, task2));

        List<TaskDto> result = taskService.getProjectTasks(projectId);

        assertEquals(2, result.size());
        assertEquals("Task 1", result.get(0).getTitle());
        assertEquals("Task 2", result.get(1).getTitle());
    }

    @Test
    void getUserTasks_ReturnsList() {
        UUID userId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .title("My Task")
                .status(TaskStatus.TO_DO)
                .assignedTo(userId)
                .build();

        when(taskRepository.findByAssignedTo(userId)).thenReturn(List.of(task));

        List<TaskDto> result = taskService.getUserTasks(userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getAssignedTo());
    }

    @Test
    void getTaskById_WhenNotFound_ThrowsException() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(com.example.sharedlib.exception.ResourceNotFoundException.class,
                () -> taskService.getTaskById(taskId));
    }
}
