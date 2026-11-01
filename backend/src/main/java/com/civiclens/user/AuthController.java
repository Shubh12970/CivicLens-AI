package com.civiclens.user;

import com.civiclens.user.UserDtos.AuthResponse;
import com.civiclens.user.UserDtos.CreateOfficialRequest;
import com.civiclens.user.UserDtos.LoginRequest;
import com.civiclens.user.UserDtos.RegisterRequest;
import com.civiclens.user.UserDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a citizen account (role is always CITIZEN)")
    @PostMapping("/api/v1/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Log in and receive a bearer token")
    @PostMapping("/api/v1/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Current user")
    @GetMapping("/api/v1/auth/me")
    public UserView me() {
        return authService.me(Authz.require().id());
    }

    @Operation(summary = "Revoke all tokens of the current user (logout everywhere)")
    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logoutEverywhere(Authz.require().id());
    }

    @Operation(summary = "Delete (anonymize) the current account; reports and audit history are kept without personal data")
    @DeleteMapping("/api/v1/auth/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe() {
        authService.deleteAccount(Authz.require().id());
    }

    @Operation(summary = "Create a DEPARTMENT_ADMIN or SYSTEM_ADMIN account (SYSTEM_ADMIN only)")
    @PostMapping("/api/v1/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createOfficial(@Valid @RequestBody CreateOfficialRequest request) {
        return authService.createOfficial(Authz.require(), request);
    }
}
