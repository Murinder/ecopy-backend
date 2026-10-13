package com.example.adminservice.model.entity;

import com.example.adminservice.model.enums.PermissionType;
import com.example.adminservice.model.enums.SystemRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;

@Entity
@Table(name = "role_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @EmbeddedId
    private RolePermissionPK id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "permission_type", nullable = false, columnDefinition = "permission_type")
    private PermissionType permissionType;

    @Column(name = "resource")
    private String resource;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePermissionPK implements Serializable {

        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.NAMED_ENUM)
        @Column(name = "role", nullable = false, columnDefinition = "system_role")
        private SystemRole role;

        @Column(name = "permission", nullable = false)
        private String permission;
    }
}
