package com.example.documentservice.model.entity;

import com.example.documentservice.model.enums.PlaceholderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "placeholders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Placeholder {

    @EmbeddedId
    private PlaceholderId id;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "data_type", nullable = false, length = 50)
    private String dataType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "placeholder_type", nullable = false, columnDefinition = "placeholder_type")
    private PlaceholderType placeholderType;

    @Column(name = "example_value", columnDefinition = "TEXT")
    private String exampleValue;

    @Column(name = "is_required")
    private Boolean isRequired;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", insertable = false, updatable = false)
    private DocumentTemplate template;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder
    public static class PlaceholderId implements Serializable {

        @Column(name = "template_id")
        private UUID templateId;

        @Column(name = "placeholder", length = 100)
        private String placeholder;
    }
}
