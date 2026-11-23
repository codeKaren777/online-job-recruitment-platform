package com.groupcc2.recruitment.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyAddressResolverTest {
    @Test
    void trustedProxyCanSupplyForwardedAddress() {
        var resolver = new TrustedProxyAddressResolver("127.0.0.1");
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void untrustedClientCannotSupplyForwardedAddress() {
        var resolver = new TrustedProxyAddressResolver("127.0.0.1");
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.25");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.25");
    }

    @Test
    void trustedProxyCidrMatchesAddress() {
        var resolver = new TrustedProxyAddressResolver("10.20.0.0/16");
        assertThat(resolver.isTrusted("10.20.33.44")).isTrue();
        assertThat(resolver.isTrusted("10.21.33.44")).isFalse();
    }
}
