package com.groupcc2.recruitment.security;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

import com.groupcc2.recruitment.users.entity.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                            Environment environment,
                                            @Value("${app.openapi.enabled:false}") boolean openApiEnabled,
                                            @Value("${app.openapi.public-in-local:false}") boolean openApiPublicInLocal)
            throws Exception {
        boolean localPublicDocs = openApiEnabled && openApiPublicInLocal
                && environment.acceptsProfiles(Profiles.of("local"));
        return http
                // REST bearer tokens only; no cookie or HTTP session authentication.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    if (localPublicDocs) {
                        authorize.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/admin/users", "/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/logout", "/api/auth/change-password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/profile", "/api/profile/documents",
                                "/api/profile/documents/*/download")
                                .hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.PUT, "/api/profile").hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.POST, "/api/profile/documents").hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.DELETE, "/api/profile/documents/{documentId}")
                                .hasRole("JOB_SEEKER")
                       
                        .requestMatchers(HttpMethod.POST, "/api/applications").hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.GET, "/api/employer/applications",
                                "/api/employer/applications/*").hasRole("EMPLOYER")
                        .requestMatchers(HttpMethod.PATCH, "/api/employer/applications/*/status").hasRole("EMPLOYER")
                        .requestMatchers(HttpMethod.GET, "/api/applications", "/api/applications/*")
                                .hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.POST, "/api/jobs").hasRole("EMPLOYER")
                        .requestMatchers(HttpMethod.GET, "/api/jobs").hasRole("JOB_SEEKER")
                        .requestMatchers(HttpMethod.GET, "/api/jobs/{jobId}").hasAnyRole("JOB_SEEKER", "EMPLOYER")
                        .requestMatchers(HttpMethod.GET, "/api/employer/jobs").hasRole("EMPLOYER")
                        .requestMatchers(HttpMethod.PUT, "/api/jobs/{jobId}").hasRole("EMPLOYER")
                        .requestMatchers(HttpMethod.PATCH, "/api/jobs/{jobId}/close").hasRole("EMPLOYER")
                       
                                // Retain the existing users registration endpoint for API compatibility.
                        .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                        // Open routes deliberately as each feature gains authorization tests.
                        .anyRequest().denyAll();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                Object roleClaim = jwt.getClaims().get("role");
                if (!(roleClaim instanceof String roleName)) {
                    throw new BadJwtException("JWT identity or role claim is invalid");
                }
                UserRole role = UserRole.valueOf(roleName);
                return List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + role.name()));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new BadJwtException("JWT identity or role claim is invalid", exception);
            }
        });
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt.secret-base64}") String secret,
                          @Value("${app.security.jwt.issuer}") String issuer,
                          @Value("${app.security.jwt.audience}") String audience,
                          AccountJwtValidator accountJwtValidator) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must be valid Base64");
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must contain at least 32 random bytes");
        }
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must not be blank");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            if (jwt.getExpiresAt() != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "Required token claims missing or invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), requiredClaims, accountJwtValidator));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
