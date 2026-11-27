package com.groupcc2.recruitment.common.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupcc2.recruitment.common.exception.ApiErrorFactory;
import com.groupcc2.recruitment.common.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties properties;
    private final RateLimitRepository repository;
    private final ObjectMapper objectMapper;
    private final TrustedProxyAddressResolver addressResolver;

    public RateLimitFilter(RateLimitProperties properties, RateLimitRepository repository, ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.addressResolver = new TrustedProxyAddressResolver(properties.getTrustedProxies());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();
        PolicyMatch match = match(method, path);
        if (match == null) {
            chain.doFilter(request, response);
            return;
        }

        if (match.policy().getRequests() < 1 || match.policy().getWindowSeconds() < 1) {
            chain.doFilter(request, response);
            return;
        }

        String clientAddress = addressResolver.resolve(request);
        HttpServletRequest requestForChain = request;
        if (match.category().equals("login") || match.category().equals("recovery")) {
            requestForChain = cacheJsonBody(request);
        }
        for (String identity : identities(requestForChain, path, clientAddress, match)) {
            String bucketKey = bucketKey(match.category(), identity, match.policy().getWindowSeconds());
            int count = repository.incrementAndGet(bucketKey, match.policy().getWindowSeconds());
            if (count > match.policy().getRequests()) {
                writeLimited(request, response, retryAfter(match.policy().getWindowSeconds()));
                return;
            }
        }
        chain.doFilter(requestForChain, response);
    }

    private PolicyMatch match(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) return null;
        if (path.equals("/api/auth/login")) return new PolicyMatch("login", properties.getLogin());
        if (path.equals("/api/auth/register")) return new PolicyMatch("registration", properties.getRegistration());
        if (path.equals("/api/auth/forgot-password")) return new PolicyMatch("recovery", properties.getRecovery());
        if (path.equals("/api/auth/verify-email")) return new PolicyMatch("verification", properties.getRecovery());
        if (path.equals("/api/auth/reset-password")) return new PolicyMatch("reset", properties.getReset());
        if (path.equals("/api/profile/documents")
                || path.equals("/api/applications")
                || path.equals("/api/employer-requests")
                || path.equals("/api/employer-requests/")) {
            return new PolicyMatch("upload", properties.getUpload());
        }
        return null;
    }

    private List<String> identities(HttpServletRequest request, String path, String clientAddress, PolicyMatch match) {
        if (match.category().equals("login") || match.category().equals("recovery")) {
            String account = accountEmail(request);
            if (!account.isBlank()) {
                return List.of("ip:" + clientAddress, "account:" + account.toLowerCase());
            }
        }
        if (match.category().equals("upload")) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                return List.of("principal:" + jwt.getSubject(), "ip:" + clientAddress);
            }
        }
        return List.of("ip:" + clientAddress);
    }

    private String accountEmail(HttpServletRequest request) {
        if (!"application/json".equalsIgnoreCase(request.getContentType()) &&
                (request.getContentType() == null ||
                        !request.getContentType().startsWith("application/json"))) return "";
        try {
            byte[] bytes = StreamUtils.copyToByteArray(request.getInputStream());
            if (bytes.length > 0) {
                JsonNode body = objectMapper.readTree(bytes);
                JsonNode email = body.get("email");
                if (email != null && email.isTextual()) return email.asText().trim();
            }
        } catch (IOException ignored) {
            return "";
        }
        return "";
    }

    private HttpServletRequest cacheJsonBody(HttpServletRequest request) throws IOException {
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        return new CachedBodyHttpServletRequest(request, body);
    }

    private String bucketKey(String category, String identity, int windowSeconds) {
        long windowStart = Instant.now().getEpochSecond() / windowSeconds;
        return hash(category + "|" + identity + "|" + windowStart);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long retryAfter(int windowSeconds) {
        long elapsed = Instant.now().getEpochSecond() % windowSeconds;
        return Math.max(1, windowSeconds - elapsed);
    }

    private void writeLimited(HttpServletRequest request, HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = ApiErrorFactory.create(
                request,
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later.",
                null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private record PolicyMatch(String category, RateLimitProperties.Policy policy) {}

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return stream.read(); }
                @Override public boolean isFinished() { return stream.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {}
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
