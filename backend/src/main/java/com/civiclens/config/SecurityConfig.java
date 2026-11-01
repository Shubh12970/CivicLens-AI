package com.civiclens.config;

import com.civiclens.city.DepartmentRepository;
import com.civiclens.common.ErrorCode;
import com.civiclens.common.ErrorResponseWriter;
import com.civiclens.user.JwtAuthFilter;
import com.civiclens.user.JwtService;
import com.civiclens.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Stateless JWT security. Route-level rules here are a coarse first gate; services re-check roles, department
 * membership and ownership for every privileged action.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UserRepository users,
                                           DepartmentRepository departments, ErrorResponseWriter errorWriter,
                                           RateLimitFilter rateLimitFilter) throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService, users, departments, errorWriter);
        http
                .csrf(csrf -> csrf.disable()) // no cookies/sessions: bearer tokens are not sent automatically by browsers
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .headers(h -> h.contentTypeOptions(c -> { }).frameOptions(f -> f.deny()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                errorWriter.write(res, ErrorCode.UNAUTHORIZED, "Authentication is required."))
                        .accessDeniedHandler((req, res, ex) ->
                                errorWriter.write(res, ErrorCode.FORBIDDEN, "You do not have permission to perform this action.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/cities/**", "/api/v1/departments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents", "/api/v1/incidents/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents/duplicate-check").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/api/v1/dashboard/**").hasAnyRole("DEPARTMENT_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    /** The rate limiter runs inside the security chain (after authentication), not as a separate servlet filter. */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = props.cors() == null || props.cors().allowedOrigins() == null
                ? List.of() : props.cors().allowedOrigins().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Trace-Id", "Retry-After", "Idempotency-Replayed", "Location"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
