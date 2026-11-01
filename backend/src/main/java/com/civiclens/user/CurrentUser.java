package com.civiclens.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/** The authenticated principal, rebuilt from the database on every request (never trusted from the token alone). */
public record CurrentUser(UUID id, String displayName, Role role, UUID departmentId, UUID departmentCityId) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isSystemAdmin() {
        return role == Role.SYSTEM_ADMIN;
    }

    public boolean isDepartmentAdmin() {
        return role == Role.DEPARTMENT_ADMIN;
    }

    public boolean isOfficial() {
        return role.isOfficial();
    }
}
