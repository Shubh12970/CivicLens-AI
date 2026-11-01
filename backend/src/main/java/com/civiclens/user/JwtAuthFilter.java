package com.civiclens.user;

import com.civiclens.city.DepartmentRepository;
import com.civiclens.common.ErrorCode;
import com.civiclens.common.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates {@code Authorization: Bearer <jwt>}. A present-but-invalid token is rejected with 401 rather than
 * silently treated as anonymous, so the frontend learns that it must log in again.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ErrorResponseWriter errorWriter;

    public JwtAuthFilter(JwtService jwtService, UserRepository users, DepartmentRepository departments,
                         ErrorResponseWriter errorWriter) {
        this.jwtService = jwtService;
        this.users = users;
        this.departments = departments;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<CurrentUser> principal = jwtService.parse(header.substring(7).trim()).flatMap(this::loadUser);
        if (principal.isEmpty()) {
            errorWriter.write(response, ErrorCode.UNAUTHORIZED, "The access token is invalid, expired or revoked.");
            return;
        }
        CurrentUser user = principal.get();
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.authorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<CurrentUser> loadUser(JwtService.TokenClaims claims) {
        return users.findById(claims.userId())
                .filter(User::isActive)
                .filter(u -> u.getTokenVersion() == claims.tokenVersion())
                .map(u -> {
                    UUID deptCity = u.getDepartmentId() == null ? null
                            : departments.findById(u.getDepartmentId()).map(d -> d.getCityId()).orElse(null);
                    return new CurrentUser(u.getId(), u.getDisplayName(), u.getRole(), u.getDepartmentId(), deptCity);
                });
    }
}
