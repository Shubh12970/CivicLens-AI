package com.civiclens.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    /** Public registration. There is deliberately no role field: every self-registered account is a CITIZEN. */
    public record RegisterRequest(
            @NotBlank(message = "email is required.") @Email(message = "email must be a valid address.")
            @Size(max = 254, message = "email must be at most 254 characters.") String email,
            @NotBlank(message = "password is required.")
            @Size(min = 8, max = 72, message = "password must be 8-72 characters.") String password,
            @NotBlank(message = "displayName is required.")
            @Size(min = 2, max = 60, message = "displayName must be 2-60 characters.")
            @Pattern(regexp = "^[^\\p{Cntrl}<>]+$", message = "displayName contains invalid characters.") String displayName,
            UUID homeCityId) {
    }

    public record LoginRequest(
            @NotBlank(message = "email is required.") @Size(max = 254) String email,
            @NotBlank(message = "password is required.") @Size(max = 72) String password) {
    }

    public record CreateOfficialRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72, message = "password must be 12-72 characters.") String password,
            @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = "^[^\\p{Cntrl}<>]+$") String displayName,
            @NotNull(message = "role is required.") Role role,
            UUID departmentId) {
    }

    public record UserView(
            UUID id,
            String email,
            String displayName,
            Role role,
            UUID departmentId,
            String departmentName,
            UUID homeCityId,
            Instant createdAt) {
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {
    }
}
