package com.groupcc2.recruitment.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {
    private RateLimitProperties properties;
    private RateLimitRepository repository;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setTrustedProxies("127.0.0.1");
        properties.getLogin().setRequests(2);
        properties.getLogin().setWindowSeconds(900);
        properties.getRegistration().setRequests(1);
        properties.getRegistration().setWindowSeconds(3600);
        repository = mock(RateLimitRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        filter = new RateLimitFilter(properties, repository, objectMapper);
    }

    @Test
    void belowLimitReachesFilterChain() throws Exception {
        when(repository.incrementAndGet(anyString(), anyInt())).thenReturn(1);
        var request = loginRequest("one@example.test");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exceededLoginReturns429AndSafeJson() throws Exception {
        when(repository.incrementAndGet(anyString(), anyInt())).thenReturn(3);
        var request = loginRequest("blocked@example.test");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("\"code\":\"RATE_LIMIT_EXCEEDED\"")
                .contains("\"message\":\"Too many requests. Please try again later.\"");
    }

    @Test
    void endpointPoliciesUseSeparateLimits() throws Exception {
        when(repository.incrementAndGet(anyString(), anyInt())).thenReturn(1);
        var registration = new MockHttpServletRequest("POST", "/api/auth/register");
        registration.setRemoteAddr("127.0.0.1");
        registration.setContentType("application/json");
        registration.setContent("{\"email\":\"new@example.test\",\"password\":\"StrongPassword123!\"}".getBytes());
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(registration, response, chain);

        verify(repository).incrementAndGet(anyString(), org.mockito.ArgumentMatchers.eq(3600));
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest loginRequest(String email) {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        request.setContentType("application/json");
        request.setContent(("{\"email\":\"" + email + "\",\"password\":\"StrongPassword123!\"}").getBytes());
        return request;
    }
}
