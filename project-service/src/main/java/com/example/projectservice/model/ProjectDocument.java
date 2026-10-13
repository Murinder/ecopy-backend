package com.example.projectservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ProjectDocument entity
 */
@Entity
@Table(name = "project_documents", indexes = {
        @Index(name = "idx_project_documents_project_id", columnList = "project_id")
})
@Data
@EqualsAndHashCode(exclude = "project")
@ToString(exclude = "project")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDocument {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "file_path", length = 1024, nullable = false)
    private String filePath;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String description;
}
