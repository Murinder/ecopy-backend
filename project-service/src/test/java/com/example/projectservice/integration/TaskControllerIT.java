package com.example.projectservice.integration;

import com.example.projectservice.client.UserServiceClient;
import com.example.projectservice.model.Project;
import com.example.projectservice.model.ProjectMember;
import com.example.projectservice.model.Task;
import com.example.projectservice.repository.ProjectMemberRepository;
import com.example.projectservice.repository.ProjectRepository;
import com.example.projectservice.repository.TaskRepository;
import com.example.sharedlib.dto.TaskDto;
import com.example.sharedlib.enums.ProjectStatus;
import com.example.sharedlib.enums.ProjectRole;
import com.example.sharedlib.enums.TaskStatus;
import com.example.sharedlib.storage.FileStorageService;
import com.example.testsupport.BaseIntegrationTest;
import com.example.testsupport.security.TestSecurityHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TaskControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private FileStorageService fileStorageService;

    private static final UUID USER_ID = UUID.randomUUID();
    private Project testProject;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();

        testProject = projectRepository.save(Project.builder()
                .id(UUID.randomUUID())
                .title("Test Project for Tasks")
                .description("desc")
                .status(ProjectStatus.ACTIVE)
                .createdBy(USER_ID)
                .build());

        projectMemberRepository.save(ProjectMember.builder()
                .id(new ProjectMember.ProjectMemberId(testProject.getId(), USER_ID))
                .project(testProject)
                .role(ProjectRole.MEMBER)
                .build());
    }

    private TaskDto buildTaskDto() {
        return TaskDto.builder()
                .projectId(testProject.getId())
                .title("Test Task")
                .description("A test task description")
                .assignedTo(USER_ID)
                .dueDate(LocalDate.now().plusWeeks(1))
                .build();
    }

    @Test
    void createTask_AsAuthenticatedUser_Returns201() throws Exception {
        TaskDto dto = buildTaskDto();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        post("/api/v1/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto)),
                        USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TASK_CREATED"))
                .andExpect(jsonPath("$.data.title").value("Test Task"))
                .andExpect(jsonPath("$.data.status").value("TO_DO"));

        assertThat(taskRepository.count()).isEqualTo(1);
    }

    @Test
    void getTask_ById_Returns200() throws Exception {
        Task task = taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("Existing Task")
                .description("desc")
                .status(TaskStatus.TO_DO)
                .assignedTo(USER_ID)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/tasks/" + task.getId()),
                        USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Existing Task"))
                .andExpect(jsonPath("$.data.projectId").value(testProject.getId().toString()));
    }

    @Test
    void getProjectTasks_ReturnsList() throws Exception {
        taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("Task 1")
                .status(TaskStatus.TO_DO)
                .build());
        taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("Task 2")
                .status(TaskStatus.IN_PROGRESS)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/tasks/project/" + testProject.getId()),
                        USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void updateTask_Returns200() throws Exception {
        Task task = taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("Original Task")
                .description("original desc")
                .status(TaskStatus.TO_DO)
                .build());

        TaskDto updateDto = TaskDto.builder()
                .title("Updated Task Title")
                .description("updated desc")
                .build();

        mockMvc.perform(TestSecurityHelper.withStudent(
                        put("/api/v1/tasks/" + task.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateDto)),
                        USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated Task Title"));
    }

    @Test
    void changeTaskStatus_TransitionsCorrectly() throws Exception {
        Task task = taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("Status Task")
                .status(TaskStatus.TO_DO)
                .assignedTo(USER_ID)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        patch("/api/v1/tasks/" + task.getId() + "/status")
                                .param("status", "IN_PROGRESS"),
                        USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        Task updated = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void deleteTask_Returns200() throws Exception {
        Task task = taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("To Delete")
                .status(TaskStatus.TO_DO)
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        delete("/api/v1/tasks/" + task.getId()),
                        USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TASK_DELETED"));

        assertThat(taskRepository.findById(task.getId())).isEmpty();
    }

    @Test
    void getUserTasks_ReturnsAssignedTasks() throws Exception {
        UUID assignee = UUID.randomUUID();
        taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("Assigned Task")
                .status(TaskStatus.TO_DO)
                .assignedTo(assignee)
                .build());
        taskRepository.save(Task.builder()
                .id(UUID.randomUUID())
                .project(testProject)
                .title("Unassigned Task")
                .status(TaskStatus.TO_DO)
                .assignedTo(UUID.randomUUID())
                .build());

        mockMvc.perform(TestSecurityHelper.withStudent(
                        get("/api/v1/tasks/user/" + assignee),
                        assignee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Assigned Task"));
    }
}
