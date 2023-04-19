/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.filter;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.telemetry.Destination.AMPLITUDE;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.ACCOUNT_IDENTIFIER;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.API_ENDPOINT;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.API_ENDPOINTS_AUTH_SCHEMES;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.API_PATTERN;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.AUTH_TYPE;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.DEFAULT_RATE_LIMIT;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.X_API_KEY;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.API_TYPE;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;
import org.glassfish.jersey.uri.UriTemplate;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PL)
@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class APIAuthTelemetryFilterTest {
  public static final String SOME_API_KEY = "some-api-key";
  public static final String SOME_ACCOUNT_ID = "some-account-id";
  public static final String SOME_API_ENDPOINT = "/some-api-endpoint";
  public static final String GET = "GET";
  public static final int CUSTOM_REQUEST_LIMIT = 10;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private ContainerRequestContext containerRequestContext;
  @Mock private UriRoutingContext uriRoutingContext;
  @Mock private MultivaluedMap<String, String> parametersMap;

  private APIAuthTelemetryFilter filter;
  private final HashMap<String, Object> properties = new HashMap<>();

  @Before
  public void setup() {
    when(containerRequestContext.getUriInfo()).thenReturn(uriRoutingContext);
    when(containerRequestContext.getMethod()).thenReturn(GET);

    when(uriRoutingContext.getPath()).thenReturn(SOME_API_ENDPOINT);
    when(uriRoutingContext.getQueryParameters()).thenReturn(parametersMap);
    when(uriRoutingContext.getPathParameters()).thenReturn(parametersMap);

    when(parametersMap.getFirst(NGCommonEntityConstants.ACCOUNT_KEY)).thenReturn(SOME_ACCOUNT_ID);
    List<UriTemplate> templates = new ArrayList<>();
    templates.add(new UriTemplate("/id"));
    templates.add(new UriTemplate("/pipelines"));
    when(uriRoutingContext.getMatchedTemplates()).thenReturn(templates);

    filter = new APIAuthTelemetryFilter(telemetryReporter);

    properties.put(ACCOUNT_IDENTIFIER, SOME_ACCOUNT_ID);
    properties.put(API_ENDPOINT, SOME_API_ENDPOINT);
    properties.put(API_TYPE, GET);
    properties.put(API_PATTERN, "/pipelines/id");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldNotSendTelemetryDataIfAccountIdentifierNotPresentInUri() {
    when(parametersMap.getFirst(NGCommonEntityConstants.ACCOUNT_KEY)).thenReturn(null);

    filter.filter(containerRequestContext);

    Mockito.verifyNoInteractions(telemetryReporter);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldNotSendTelemetryIfApiKeyIsNotPresent() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(null);

    filter.filter(containerRequestContext);

    Mockito.verifyNoInteractions(telemetryReporter);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldSendTelemetryForApiKey() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    properties.put(AUTH_TYPE, X_API_KEY);

    filter.filter(containerRequestContext);

    verify(telemetryReporter)
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldSendTelemetryForApiKeyIfNumberOfRequestsLowerThanDefaultRateLimit() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    properties.put(AUTH_TYPE, X_API_KEY);

    for (int numberOfRequest = 0; numberOfRequest < DEFAULT_RATE_LIMIT - 1; numberOfRequest++) {
      filter.filter(containerRequestContext);
    }

    verify(telemetryReporter, times(DEFAULT_RATE_LIMIT - 1))
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldSendTelemetryForApiKeyIfNumberOfRequestsEqualsToDefaultRateLimit() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    properties.put(AUTH_TYPE, X_API_KEY);

    for (int numberOfRequest = 0; numberOfRequest < DEFAULT_RATE_LIMIT; numberOfRequest++) {
      filter.filter(containerRequestContext);
    }

    verify(telemetryReporter, times(DEFAULT_RATE_LIMIT))
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldNotThrowExceptionIfNumberOfRequestsMoreThanDefaultRateLimit() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    properties.put(AUTH_TYPE, X_API_KEY);

    for (int numberOfRequest = 0; numberOfRequest < DEFAULT_RATE_LIMIT + 1; numberOfRequest++) {
      filter.filter(containerRequestContext);
    }

    verify(telemetryReporter, times(DEFAULT_RATE_LIMIT))
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldBeAbleToOverrideDefaultRateLimit() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    properties.put(AUTH_TYPE, X_API_KEY);

    RateLimiterConfig overriddenRateLimiterConfig = RateLimiterConfig.custom()
                                                        .limitForPeriod(CUSTOM_REQUEST_LIMIT)
                                                        .limitRefreshPeriod(Duration.ofMinutes(1))
                                                        .build();
    filter = new APIAuthTelemetryFilter(telemetryReporter, overriddenRateLimiterConfig);

    for (int numberOfRequest = 0; numberOfRequest < CUSTOM_REQUEST_LIMIT + 1; numberOfRequest++) {
      filter.filter(containerRequestContext);
    }

    verify(telemetryReporter, times(CUSTOM_REQUEST_LIMIT))
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }
}
