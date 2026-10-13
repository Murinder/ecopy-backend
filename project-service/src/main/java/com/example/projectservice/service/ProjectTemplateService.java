package com.example.projectservice.service;

import com.example.projectservice.dto.ProjectTemplateDto;
import com.example.projectservice.model.ProjectTemplate;
import com.example.projectservice.repository.ProjectTemplateRepository;
import com.example.sharedlib.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service для управления шаблонами проектов
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectTemplateService {

    private final ProjectTemplateRepository projectTemplateRepository;

    public ProjectTemplateDto createTemplate(ProjectTemplateDto templateDto) {
        ProjectTemplate template = toEntity(templateDto);
        ProjectTemplate savedTemplate = projectTemplateRepository.save(template);
        return toDto(savedTemplate);
    }

    @Transactional(readOnly = true)
    public ProjectTemplateDto getTemplateById(UUID templateId) {
        return projectTemplateRepository.findById(templateId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
    }

    @Transactional(readOnly = true)
    public List<ProjectTemplateDto> getAllTemplates() {
        return projectTemplateRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    private ProjectTemplateDto toDto(ProjectTemplate template) {
        return ProjectTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .templateType(template.getTemplateType())
                .config(template.getConfig())
                .createdBy(template.getCreatedBy())
                .createdAt(template.getCreatedAt())
                .build();
    }

    private ProjectTemplate toEntity(ProjectTemplateDto dto) {
        return ProjectTemplate.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .templateType(dto.getTemplateType())
                .config(dto.getConfig())
                .createdBy(dto.getCreatedBy())
                .build();
    }
}
