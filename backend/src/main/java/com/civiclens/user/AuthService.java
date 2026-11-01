package com.civiclens.user;

import com.civiclens.city.CityDirectory;
import com.civiclens.city.Department;
import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.civiclens.user.UserDtos.AuthResponse;
import com.civiclens.user.UserDtos.CreateOfficialRequest;
import com.civiclens.user.UserDtos.LoginRequest;
import com.civiclens.user.UserDtos.RegisterRequest;
import com.civiclens.user.UserDtos.UserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CityDirectory cityDirectory;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, CityDirectory cityDirectory,
                       Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.cityDirectory = cityDirectory;
        this.clock = clock;
        // Used to keep login timing similar whether or not the email exists.
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        if (req.homeCityId() != null) {
            cityDirectory.requireActiveCity(req.homeCityId());
        }
        User user = new User(email, encoder.encode(req.password()), req.displayName().strip(), Role.CITIZEN, null,
                req.homeCityId(), clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        log.info("user-registered userId={} role=CITIZEN", user.getId());
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(req.email())).filter(User::isActive).orElse(null);
        if (user == null) {
            encoder.matches(req.password(), dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            log.info("login-failed userId={}", user.getId());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect.");
        }
        return tokenFor(user);
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.revokeTokens(clock.instant());
        log.info("user-logout userId={}", userId);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw ApiException.forbidden("System administrator accounts cannot be self-deleted.");
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        user.anonymize(encoder.encode(Base64.getEncoder().encodeToString(random)), clock.instant());
        log.info("user-anonymized userId={}", userId);
    }

    @Transactional(readOnly = true)
    public UserView me(UUID userId) {
        return toView(users.findById(userId).filter(User::isActive).orElseThrow(() -> ApiException.notFound("User")));
    }

    /** SYSTEM_ADMIN only (enforced by route rule and here). */
    @Transactional
    public UserView createOfficial(CurrentUser actor, CreateOfficialRequest req) {
        if (!actor.isSystemAdmin()) {
            throw ApiException.forbidden("Only a system administrator can create official accounts.");
        }
        if (req.role() == Role.CITIZEN) {
            throw ApiException.validation("role", "Use public registration for citizen accounts.");
        }
        UUID departmentId = null;
        if (req.role() == Role.DEPARTMENT_ADMIN) {
            if (req.departmentId() == null) {
                throw ApiException.validation("departmentId", "departmentId is required for DEPARTMENT_ADMIN.");
            }
            departmentId = cityDirectory.requireDepartment(req.departmentId()).getId();
        } else if (req.departmentId() != null) {
            throw ApiException.validation("departmentId", "departmentId must be empty for SYSTEM_ADMIN.");
        }
        String email = normalizeEmail(req.email());
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        User user = new User(email, encoder.encode(req.password()), req.displayName().strip(), req.role(), departmentId,
                null, clock.instant());
        users.saveAndFlush(user);
        log.info("official-created userId={} role={} departmentId={} by={}", user.getId(), req.role(), departmentId, actor.id());
        return toView(user);
    }

    public UserView toView(User u) {
        String deptName = cityDirectory.findDepartment(u.getDepartmentId()).map(Department::getName).orElse(null);
        return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.getDepartmentId(), deptName,
                u.getHomeCityId(), u.getCreatedAt());
    }

    private AuthResponse tokenFor(User user) {
        JwtService.IssuedToken token = jwt.issue(user);
        return new AuthResponse(token.token(), "Bearer", token.expiresAt(), toView(user));
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
