package com.kovospace.newtablinks.statistics.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kovospace.newtablinks.admin.services.AdminAccessTokenIssuer;
import com.kovospace.newtablinks.auth.config.AuthenticationProperties;
import com.kovospace.newtablinks.auth.config.WebApplicationProperties;
import com.kovospace.newtablinks.auth.services.ProviderSignInSuccessHandler;
import com.kovospace.newtablinks.auth.services.VisitorTokenService;
import com.kovospace.newtablinks.common.config.ApiEndpointPaths;
import com.kovospace.newtablinks.common.config.SecurityConfiguration;
import com.kovospace.newtablinks.common.exceptions.RequestRateLimitExceededException;
import com.kovospace.newtablinks.statistics.dtos.UsageMetricMonthDto;
import com.kovospace.newtablinks.statistics.dtos.UsageReportAcknowledgementDto;
import com.kovospace.newtablinks.statistics.models.UsageMetric;
import com.kovospace.newtablinks.statistics.services.NewTabReportService;
import com.kovospace.newtablinks.statistics.services.UsageMetricReportService;
import com.kovospace.newtablinks.statistics.services.UsageStatisticsRateLimiter;
import com.kovospace.newtablinks.statistics.services.WebsiteVisitService;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks, through the real security configuration, who may reach the statistics endpoints and
 * how a malformed or excessive call is answered.
 *
 * @since 0.0.11
 */
@WebMvcTest({UsageStatisticsController.class, AdminUsageMetricsController.class})
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({AuthenticationProperties.class, WebApplicationProperties.class})
class UsageStatisticsControllerTest {

    private static final String BROWSER =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewTabReportService newTabReportService;

    @MockitoBean
    private WebsiteVisitService websiteVisitService;

    @MockitoBean
    private UsageStatisticsRateLimiter usageStatisticsRateLimiter;

    @MockitoBean
    private UsageMetricReportService usageMetricReportService;

    @MockitoBean
    private VisitorTokenService visitorTokenService;

    @MockitoBean
    private ProviderSignInSuccessHandler providerSignInSuccessHandler;

    @Test
    @DisplayName("takes a new-tab report with no token and answers the reporting interval")
    void shouldAcceptAnonymousNewTabReport() throws Exception {
        when(newTabReportService.recordReport(any()))
                .thenReturn(new UsageReportAcknowledgementDto(900));

        reportNewTabs("{\"days\":[{\"day\":\"2026-09-28\",\"count\":12}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextReportAfterSeconds").value(900));
    }

    @Test
    @DisplayName("ignores an Authorization header on a new-tab report, even an invalid one")
    void shouldIgnoreAuthorizationHeaderOnNewTabReport() throws Exception {
        when(newTabReportService.recordReport(any()))
                .thenReturn(new UsageReportAcknowledgementDto(900));

        mockMvc.perform(post(ApiEndpointPaths.NEW_TAB_STATISTICS_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refuses a new-tab report that is not JSON with 400")
    void shouldRefuseUnreadableNewTabReport() throws Exception {
        reportNewTabs("{\"days\":").andExpect(status().isBadRequest());
        verify(newTabReportService, never()).recordReport(any());
    }

    @Test
    @DisplayName("refuses a new-tab report without days, or with an impossible date, with 400")
    void shouldRefuseMalformedNewTabReport() throws Exception {
        reportNewTabs("{}").andExpect(status().isBadRequest());
        reportNewTabs("{\"days\":[{\"day\":\"2026-13-01\",\"count\":1}]}")
                .andExpect(status().isBadRequest());
        reportNewTabs("{\"days\":[{\"day\":\"2026-09-28\"}]}").andExpect(status().isBadRequest());
        verify(newTabReportService, never()).recordReport(any());
    }

    @Test
    @DisplayName("refuses a new-tab report of more than eight days with 400")
    void shouldRefuseNewTabReportOfMoreThanEightDays() throws Exception {
        final String nineDays = IntStream.rangeClosed(1, 9)
                .mapToObj(day -> "{\"day\":\"2026-09-%02d\",\"count\":1}".formatted(day))
                .collect(Collectors.joining(",", "{\"days\":[", "]}"));

        reportNewTabs(nineDays).andExpect(status().isBadRequest());
        verify(newTabReportService, never()).recordReport(any());
    }

    @Test
    @DisplayName("answers a rate-limited new-tab report with 429 and Retry-After")
    void shouldAnswerRateLimitedNewTabReportWith429() throws Exception {
        doThrow(new RequestRateLimitExceededException(Duration.ofMinutes(10)))
                .when(usageStatisticsRateLimiter).acquirePermit(eq(UsageMetric.NEW_TABS), anyString());

        reportNewTabs("{\"days\":[]}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "600"));
        verify(newTabReportService, never()).recordReport(any());
    }

    @Test
    @DisplayName("takes a website visit with no token and no body, passing the user agent on")
    void shouldAcceptAnonymousWebsiteVisit() throws Exception {
        mockMvc.perform(post(ApiEndpointPaths.WEBSITE_VISIT_STATISTICS_PATH)
                        .header(HttpHeaders.USER_AGENT, BROWSER)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        }))
                .andExpect(status().isNoContent());

        verify(usageStatisticsRateLimiter)
                .acquirePermit(UsageMetric.WEBSITE_VISITORS, "203.0.113.7");
        verify(websiteVisitService).recordVisit("203.0.113.7", BROWSER);
    }

    @Test
    @DisplayName("does not open the admin metrics to an anonymous caller or a user token")
    void shouldGuardAdminMetrics() throws Exception {
        mockMvc.perform(get(adminMetricsPath("new_tabs", "2026-09")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(adminMetricsPath("new_tabs", "2026-09"))
                        .with(jwt().jwt(token -> token.subject(
                                "00000000-0000-0000-0000-000000000042"))))
                .andExpect(status().isForbidden());
        verify(usageMetricReportService, never()).describeMonth(any(), any());
    }

    @Test
    @DisplayName("answers an admin with the month the service describes")
    void shouldAnswerAdminWithMonth() throws Exception {
        when(usageMetricReportService.describeMonth(
                UsageMetric.WEBSITE_VISITORS, YearMonth.parse("2026-02")))
                .thenReturn(new UsageMetricMonthDto("website_visitors", "2026-02", List.of(), 0));

        mockMvc.perform(get(adminMetricsPath("website_visitors", "2026-02")).with(adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("website_visitors"))
                .andExpect(jsonPath("$.month").value("2026-02"));
    }

    @Test
    @DisplayName("refuses an unknown metric or a malformed month with 400")
    void shouldRefuseUnknownMetricOrMalformedMonth() throws Exception {
        mockMvc.perform(get(adminMetricsPath("page_views", "2026-09")).with(adminToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(adminMetricsPath("new_tabs", "2026-9-01")).with(adminToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiEndpointPaths.ADMINISTRATION_BASE_PATH
                        + ApiEndpointPaths.ADMINISTRATION_METRICS_SUBPATH + "?metric=new_tabs")
                        .with(adminToken()))
                .andExpect(status().isBadRequest());
        verify(usageMetricReportService, never()).describeMonth(any(), any());
    }

    /**
     * Posts a new-tab report as the extension does: JSON, and no credentials.
     *
     * @param json the body
     * @return the result, for expectations
     * @throws Exception when the request cannot be performed
     */
    private ResultActions reportNewTabs(final String json) throws Exception {
        return mockMvc.perform(post(ApiEndpointPaths.NEW_TAB_STATISTICS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /**
     * Builds the admin metrics path with its query.
     *
     * @param metric the metric parameter
     * @param month  the month parameter
     * @return the path
     */
    private static String adminMetricsPath(final String metric, final String month) {
        return ApiEndpointPaths.ADMINISTRATION_BASE_PATH
                + ApiEndpointPaths.ADMINISTRATION_METRICS_SUBPATH
                + "?metric=" + metric + "&month=" + month;
    }

    /**
     * Presents an operator's token.
     *
     * @return the request post-processor
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminToken() {
        return jwt().jwt(token -> token.subject("admin"))
                .authorities(new SimpleGrantedAuthority(AdminAccessTokenIssuer.ADMIN_AUTHORITY));
    }
}
