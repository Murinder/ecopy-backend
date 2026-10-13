package com.example.projectservice.model;

import com.example.sharedlib.enums.ProjectRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ProjectMember entity
 */
@Entity
@Table(name = "project_members")
@Data
@EqualsAndHashCode(exclude = "project")
@ToString(exclude = "project")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMember {
    @EmbeddedId
    private ProjectMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    private Project project;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "project_role")
    @Builder.Default
    private ProjectRole role = ProjectRole.MEMBER;

    @Column(name = "joined_at")
    @Builder.Default
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectMemberId {
        private UUID projectId;
        private UUID userId;
    }
}
