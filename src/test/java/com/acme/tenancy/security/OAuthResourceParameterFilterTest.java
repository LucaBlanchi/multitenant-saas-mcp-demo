package com.acme.tenancy.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthResourceParameterFilterTest {
    private static final String RESOURCE = "https://mcp.example.test/mcp";

    @Test void explicitWrongResourceIsRejected() throws Exception { reject("https://other.example.test/mcp"); }
    @Test void multipleResourcesAreRejected() throws Exception { reject(RESOURCE, RESOURCE); }
    @Test void exactResourceIsAccepted() throws Exception {
        var chain = mock(FilterChain.class);
        var request = request();
        request.addParameter("resource", RESOURCE);
        var response = new MockHttpServletResponse();
        new OAuthResourceParameterFilter(RESOURCE).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    @Test void consentWithoutResourceUsesTheSingleServerAudience() throws Exception {
        var chain = mock(FilterChain.class);
        var request = request();
        var response = new MockHttpServletResponse();
        new OAuthResourceParameterFilter(RESOURCE).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    private void reject(String... resources) throws Exception {
        var chain = mock(FilterChain.class);
        var request = request();
        request.addParameter("resource", resources);
        var response = new MockHttpServletResponse();
        new OAuthResourceParameterFilter(RESOURCE).doFilter(request, response, chain);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("invalid_target"));
        verifyNoInteractions(chain);
    }
    private MockHttpServletRequest request() { return new MockHttpServletRequest("POST", "/oauth2/token"); }
}
