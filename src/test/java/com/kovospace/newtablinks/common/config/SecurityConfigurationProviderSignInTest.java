package com.kovospace.newtablinks.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.security.CookieOAuth2AuthorizationRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Checks, through the real security configuration, that starting a Google sign-in keeps its state
 * in a cookie and creates no HTTP session.
 *
 * <p>A session is what tied the sign-in to one pod: the callback had to reach the replica holding
 * it or fail. This fails if the chain is ever wired back to Spring's session-backed default.</p>
 *
 * @since 0.0.12
 */
@WebMvcTest(useDefaultFilters = false)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({AuthenticationProperties.class, WebApplicationProperties.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "spring.security.oauth2.client.registration.google.scope=openid,email,profile"
})
class SecurityConfigurationProviderSignInTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisitorTokenService visitorTokenService;

    @MockitoBean
    private ProviderSignInSuccessHandler providerSignInSuccessHandler;

    @Test
    @DisplayName("starting a Google sign-in sets the sealed cookie and creates no session")
    void shouldKeepTheAuthorizationRequestInACookieRatherThanASession() throws Exception {
        final MvcResult result = mockMvc.perform(get("/oauth2/authorization/google")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).startsWith("https://accounts.google.com/");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(setCookie -> assertThat(setCookie)
                        .startsWith(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "="));
        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
