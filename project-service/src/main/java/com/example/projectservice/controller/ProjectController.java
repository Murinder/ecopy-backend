package com.example.projectservice.controller;

import com.example.sharedlib.dto.DepartmentDashboardDto;
import com.example.sharedlib.dto.ProjectDashboardStatsDto;
import com.example.sharedlib.dto.ProjectDto;
import com.example.sharedlib.enums.ProjectStatus;
import com.example.projectservice.dto.TeacherProjectStatsDto;
import com.example.projectservice.dto.UserProjectStatsDto;
import com.example.projectservice.service.ProjectService;
import com.example.sharedlib.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.example.sharedlib.security.LecturerOrAbove;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Project Controller
 * Управляет операциями с проектами (CRUD)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Endpoints для управления проектами")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * Создать новый проект
     */
    @LecturerOrAbove
    @PostMapping
    @Operation(summary = "Создать новый проект", description = "Создает новый проект с указанными параметрами")
    public ResponseEntity<ApiResponse<ProjectDto>> createProject(
            @RequestBody ProjectDto projectDto,
            Authentication authentication) {
        log.info("Creating new project: {}", projectDto.getTitle());

        projectDto.setCreatedBy(UUID.fromString(authentication.getName()));

        ProjectDto createdProject = projectService.createProject(projectDto);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<ProjectDto>builder()
                        .success(true)
                        .code("PROJECT_CREATED")
                        .message("Project created successfully")
                        .data(createdProject)
                        .build());
    }

    /**
     * Получить проект по ID
     */
    @GetMapping("/{projectId}")
    @Operation(summary = "Получить проект", description = "Возвращает информацию о проекте")
    @PreAuthorize("@projectSecurity.isMember(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<ProjectDto>> getProject(@PathVariable UUID projectId) {
        log.info("Getting project: {}", projectId);

        ProjectDto project = projectService.getProjectById(projectId);

        return ResponseEntity.ok(ApiResponse.<ProjectDto>builder()
                .success(true)
                .code("PROJECT_RETRIEVED")
                .message("Project retrieved successfully")
                .data(project)
                .build());
    }

    /**
     * Получить проекты пользователя
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Получить проекты пользователя", description = "Возвращает все проекты, созданные пользователем")
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getUserProjects(@PathVariable String userId) {
        log.info("Getting projects for user: {}", userId);

        List<ProjectDto> projects = projectService.getUserProjects(UUID.fromString(userId));

        return ResponseEntity.ok(ApiResponse.<List<ProjectDto>>builder()
                .success(true)
                .code("USER_PROJECTS_RETRIEVED")
                .message("User projects retrieved successfully")
                .data(projects)
                .build());
    }

    /**
     * Получить проекты кафедры
     */
    @GetMapping("/department/{departmentId}")
    @Operation(summary = "Получить проекты кафедры", description = "Возвращает все активные проекты кафедры")
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getDepartmentProjects(@PathVariable String departmentId) {
        log.info("Getting projects for department: {}", departmentId);

        List<ProjectDto> projects = projectService.getDepartmentProjects(UUID.fromString(departmentId));

        return ResponseEntity.ok(ApiResponse.<List<ProjectDto>>builder()
                .success(true)
                .code("DEPARTMENT_PROJECTS_RETRIEVED")
                .message("Department projects retrieved successfully")
                .data(projects)
                .build());
    }

    /**
     * Обновить проект
     */
    @PutMapping("/{projectId}")
    @Operation(summary = "Обновить проект", description = "Обновляет информацию о проекте")
    @PreAuthorize("@projectSecurity.isLeader(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<ProjectDto>> updateProject(
            @PathVariable UUID projectId,
            @RequestBody ProjectDto updateDto) {
        log.info("Updating project: {}", projectId);

        ProjectDto updatedProject = projectService.updateProject(projectId, updateDto);

        return ResponseEntity.ok(ApiResponse.<ProjectDto>builder()
                .success(true)
                .code("PROJECT_UPDATED")
                .message("Project updated successfully")
                .data(updatedProject)
                .build());
    }

    /**
     * Изменить статус проекта
     */
    @PatchMapping("/{projectId}/status")
    @Operation(summary = "Изменить статус проекта", description = "Изменяет статус проекта (active, completed, frozen, cancelled)")
    @PreAuthorize("@projectSecurity.isLeader(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<ProjectDto>> changeProjectStatus(
            @PathVariable UUID projectId,
            @RequestParam ProjectStatus status) {
        log.info("Changing project status: {}", projectId);

        ProjectDto updatedProject = projectService.changeProjectStatus(projectId, status);

        return ResponseEntity.ok(ApiResponse.<ProjectDto>builder()
                .success(true)
                .code("PROJECT_STATUS_CHANGED")
                .message("Project status changed successfully")
                .data(updatedProject)
                .build());
    }

    /**
     * Получить статистику проектов пользователя
     */
    @GetMapping("/user/{userId}/stats")
    @Operation(summary = "Статистика проектов пользователя", description = "Возвращает количество проектов по статусам")
    public ResponseEntity<ApiResponse<UserProjectStatsDto>> getUserProjectStats(@PathVariable UUID userId) {
        log.info("Getting project stats for user: {}", userId);
        UserProjectStatsDto stats = projectService.getUserProjectStats(userId);
        return ResponseEntity.ok(ApiResponse.<UserProjectStatsDto>builder()
                .success(true)
                .code("PROJECT_STATS_RETRIEVED")
                .message("Project stats retrieved successfully")
                .data(stats)
                .build());
    }

    /**
     * Получить детальную статистику проектов пользователя (задачи, участники)
     */
    @GetMapping("/user/{userId}/detailed-stats")
    @Operation(summary = "Детальная статистика проектов", description = "Возвращает проекты с задачами и участниками для дашборда")
    public ResponseEntity<ApiResponse<ProjectDashboardStatsDto>> getUserProjectDetailedStats(@PathVariable UUID userId) {
        log.info("Getting detailed project stats for user: {}", userId);
        ProjectDashboardStatsDto stats = projectService.getUserProjectDetailedStats(userId);
        return ResponseEntity.ok(ApiResponse.<ProjectDashboardStatsDto>builder()
                .success(true)
                .code("DETAILED_STATS_RETRIEVED")
                .message("Detailed project stats retrieved successfully")
                .data(stats)
                .build());
    }

    /**
     * Получить дашборд кафедры
     */
    @GetMapping("/department/{departmentId}/dashboard")
    @Operation(summary = "Дашборд кафедры", description = "Возвращает агрегированные данные по проектам кафедры")
    public ResponseEntity<ApiResponse<DepartmentDashboardDto>> getDepartmentDashboard(@PathVariable UUID departmentId) {
        log.info("Getting department dashboard: {}", departmentId);
        DepartmentDashboardDto dashboard = projectService.getDepartmentDashboard(UUID.fromString(String.valueOf(departmentId)));
        return ResponseEntity.ok(ApiResponse.<DepartmentDashboardDto>builder()
                .success(true)
                .code("DEPARTMENT_DASHBOARD_RETRIEVED")
                .message("Department dashboard retrieved successfully")
                .data(dashboard)
                .build());
    }

    /**
     * Batch: получить статистику преподавателей по проектам
     */
    @PostMapping("/batch/teacher-stats")
    @Operation(summary = "Статистика преподавателей", description = "Возвращает статистику проектов и студентов для списка преподавателей")
    public ResponseEntity<ApiResponse<List<TeacherProjectStatsDto>>> getBatchTeacherStats(@RequestBody List<UUID> teacherIds) {
        log.info("Getting batch teacher stats for {} teachers", teacherIds.size());
        List<TeacherProjectStatsDto> stats = projectService.getBatchTeacherStats(teacherIds);
        return ResponseEntity.ok(ApiResponse.<List<TeacherProjectStatsDto>>builder()
                .success(true)
                .code("TEACHER_STATS_RETRIEVED")
                .message("Teacher project stats retrieved successfully")
                .data(stats)
                .build());
    }

    /**
     * Удалить проект
     */
    @DeleteMapping("/{projectId}")
    @Operation(summary = "Удалить проект", description = "Удаляет проект из системы")
    @PreAuthorize("@projectSecurity.isLeader(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable UUID projectId) {
        log.info("Deleting project: {}", projectId);

        projectService.deleteProject(projectId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .code("PROJECT_DELETED")
                .message("Project deleted successfully")
                .build());
    }
}
