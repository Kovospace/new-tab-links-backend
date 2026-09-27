package com.kovospace.newtablinks.user.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.admin.services.AdminAccessTokenIssuer;
import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.SecurityConfiguration;
import com.kovospace.newtablinks.common.exceptions.PaidEntitlementRevocationException;
import com.kovospace.newtablinks.common.security.AuthenticatedUserProvider;
import com.kovospace.newtablinks.user.dtos.AdminUserUpdateRequestDto;
import com.kovospace.newtablinks.user.models.UserAccountStatus;
import com.kovospace.newtablinks.user.services.UserAdministrationService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks, through the real security configuration and error handling, that the operator's
 * premium checkbox reaches the service and that revoking paid pro answers 409.
 *
 * @since 0.0.10
 */
@WebMvcTest(AdminUserController.class)
@Import({SecurityConfiguration.class, AuthenticatedUserProvider.class})
@EnableConfigurationProperties({AuthenticationProperties.class, WebApplicationProperties.class})
class AdminUserControllerPremiumTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final String ACCOUNT_PATH =
            ApiEndpointPaths.ADMINISTRATION_BASE_PATH + "/users/" + ACCOUNT_ID;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAdministrationService userAdministrationService;

    @MockitoBean
    private VisitorTokenService visitorTokenService;

    @MockitoBean
    private ProviderSignInSuccessHandler providerSignInSuccessHandler;

    @Test
    @DisplayName("revoking pro that was paid for answers 409 with the uniform error body")
    void shouldAnswerConflictWhenRevokingPaidPro() throws Exception {
        when(userAdministrationService.updateAccount(eq(ACCOUNT_ID), any()))
                .thenThrow(new PaidEntitlementRevocationException("This account is premium "
                        + "through a payment."));

        mockMvc.perform(put(ACCOUNT_PATH)
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority(AdminAccessTokenIssuer.ADMIN_AUTHORITY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"buyer@example.com","displayName":"Buyer",
                                 "status":"ACTIVE","premium":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("This account is premium through a payment."));

        verify(userAdministrationService).updateAccount(ACCOUNT_ID, new AdminUserUpdateRequestDto(
                "buyer@example.com", "Buyer", UserAccountStatus.ACTIVE, false));
    }

    @Test
    @DisplayName("a body without premium reaches the service as null, not as false")
    void shouldPassAnOmittedPremiumAsNull() throws Exception {
        mockMvc.perform(put(ACCOUNT_PATH)
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority(AdminAccessTokenIssuer.ADMIN_AUTHORITY)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"buyer@example.com","displayName":"Buyer",
                                 "status":"ACTIVE"}
                                """))
                .andExpect(status().isOk());

        verify(userAdministrationService).updateAccount(ACCOUNT_ID, new AdminUserUpdateRequestDto(
                "buyer@example.com", "Buyer", UserAccountStatus.ACTIVE, null));
    }
}
