package com.paulcartagena.icarapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver("website-secret");

    @Test
    void usesTheClientIpHeaderWithTheRightSecret() {
        assertThat(resolver.resolve(request("website-secret", "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(resolver.resolve(request("website-secret", "2001:db8::1"))).isEqualTo("2001:db8::1");
    }

    @Test
    void fallsBackToTheRemoteAddressWithoutTheRightSecret() {
        assertThat(resolver.resolve(request(null, "203.0.113.7"))).isEqualTo("10.0.0.1");
        assertThat(resolver.resolve(request("wrong-secret", "203.0.113.7"))).isEqualTo("10.0.0.1");
        assertThat(resolver.resolve(request("website-secret", null))).isEqualTo("10.0.0.1");
    }

    @Test
    void ignoresValuesThatAreNotAnIp() {
        assertThat(resolver.resolve(request("website-secret", "203.0.113.7, 10.0.0.9"))).isEqualTo("10.0.0.1");
        assertThat(resolver.resolve(request("website-secret", "not-an-ip"))).isEqualTo("10.0.0.1");
    }

    @Test
    void trustsNobodyWithoutAConfiguredSecret() {
        ClientIpResolver withoutSecret = new ClientIpResolver("");

        assertThat(withoutSecret.resolve(request("", "203.0.113.7"))).isEqualTo("10.0.0.1");
    }

    private static MockHttpServletRequest request(String secret, String clientIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        if (secret != null) {
            request.addHeader(ClientIpResolver.SECRET_HEADER, secret);
        }
        if (clientIp != null) {
            request.addHeader(ClientIpResolver.CLIENT_IP_HEADER, clientIp);
        }
        return request;
    }
}
