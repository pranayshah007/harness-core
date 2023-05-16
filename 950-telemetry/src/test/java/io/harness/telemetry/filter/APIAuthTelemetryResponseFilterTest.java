/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.filter;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.RICHA;
import static io.harness.telemetry.Destination.AMPLITUDE;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.ACCOUNT_IDENTIFIER;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.API_ENDPOINT;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.API_ENDPOINTS_ERRORED_RESPONSE;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.API_PATTERN;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.API_TYPE;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.AUTH_TYPE;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.DEFAULT_RATE_LIMIT;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.ERROR_MESSAGE;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.RESPONSE_CODE;
import static io.harness.telemetry.filter.APIAuthTelemetryResponseFilter.X_API_KEY;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ErrorDTO;
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
import javax.ws.rs.container.ContainerResponseContext;
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
public class APIAuthTelemetryResponseFilterTest extends CategoryTest {
  public static final String SOME_API_KEY = "some-api-key";
  public static final String SOME_ACCOUNT_ID = "some-account-id";
  public static final int ERROR_RESPONSE_CODE = 400;
  public static final int SUCCESS_RESPONSE_CODE = 200;
  public static final String SOME_ERROR_MESSAGE = "some-error-message";
  public static final String SOME_API_ENDPOINT = "/some-api-endpoint";
  public static final String GET = "GET";
  public static final int CUSTOM_REQUEST_LIMIT = 10;
  public static final String SOME_API_PATTERN = "some-api-pattern";
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private ContainerRequestContext containerRequestContext;
  @Mock private ContainerResponseContext containerResponseContext;
  @Mock private UriRoutingContext uriRoutingContext;
  @Mock private ErrorDTO errorDTO;
  @Mock private MultivaluedMap<String, String> parametersMap;

  private APIAuthTelemetryResponseFilter filter;
  private final HashMap<String, Object> properties = new HashMap<>();

  @Before
  public void setup() {
    when(containerRequestContext.getUriInfo()).thenReturn(uriRoutingContext);
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    when(containerRequestContext.getMethod()).thenReturn(GET);
    when(containerResponseContext.getEntity()).thenReturn(errorDTO);
    when(containerResponseContext.getStatus()).thenReturn(ERROR_RESPONSE_CODE);

    when(errorDTO.getMessage()).thenReturn(SOME_ERROR_MESSAGE);

    when(uriRoutingContext.getPath()).thenReturn(SOME_API_ENDPOINT);
    when(uriRoutingContext.getQueryParameters()).thenReturn(parametersMap);
    when(uriRoutingContext.getPathParameters()).thenReturn(parametersMap);
    List<UriTemplate> templates = new ArrayList<>();
    templates.add(new UriTemplate(SOME_API_PATTERN));
    when(uriRoutingContext.getMatchedTemplates()).thenReturn(templates);

    when(parametersMap.getFirst(NGCommonEntityConstants.ACCOUNT_KEY)).thenReturn(SOME_ACCOUNT_ID);

    filter = new APIAuthTelemetryResponseFilter(telemetryReporter);

    properties.put(AUTH_TYPE, X_API_KEY);
    properties.put(ACCOUNT_IDENTIFIER, SOME_ACCOUNT_ID);
    properties.put(API_ENDPOINT, SOME_API_ENDPOINT);
    properties.put(RESPONSE_CODE, ERROR_RESPONSE_CODE);
    properties.put(ERROR_MESSAGE, SOME_ERROR_MESSAGE);
    properties.put(API_TYPE, GET);
    properties.put(API_PATTERN, SOME_API_PATTERN);
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldNotSendTelemetryDataIfAccountIdentifierNotPresentInUri() {
    when(parametersMap.getFirst(NGCommonEntityConstants.ACCOUNT_KEY)).thenReturn(null);

    filter.filter(containerRequestContext, containerResponseContext);

    Mockito.verifyNoInteractions(telemetryReporter);
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldNotSendTelemetryIfApiKeyIsNotPresent() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(null);

    filter.filter(containerRequestContext, containerResponseContext);

    Mockito.verifyNoInteractions(telemetryReporter);
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldNotSendTelemetryIfResponseIsOkay() {
    when(containerResponseContext.getStatus()).thenReturn(SUCCESS_RESPONSE_CODE);

    filter.filter(containerRequestContext, containerResponseContext);

    Mockito.verifyNoInteractions(telemetryReporter);
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldSendTelemetryIfResponseHasErrored() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    when(containerResponseContext.getStatus()).thenReturn(ERROR_RESPONSE_CODE);

    filter.filter(containerRequestContext, containerResponseContext);

    verify(telemetryReporter)
        .sendTrackEvent(API_ENDPOINTS_ERRORED_RESPONSE, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldSendTelemetryForApiKey() {
    properties.put(AUTH_TYPE, X_API_KEY);
    properties.put(RESPONSE_CODE, ERROR_RESPONSE_CODE);
    properties.put(ERROR_MESSAGE, SOME_ERROR_MESSAGE);

    filter.filter(containerRequestContext, containerResponseContext);

    verify(telemetryReporter)
        .sendTrackEvent(API_ENDPOINTS_ERRORED_RESPONSE, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldSendTelemetryForApiKeyIfNumberOfRequestsLowerThanDefaultRateLimit() {
    for (int numberOfRequest = 0; numberOfRequest < DEFAULT_RATE_LIMIT - 1; numberOfRequest++) {
      filter.filter(containerRequestContext, containerResponseContext);
    }

    verify(telemetryReporter, times(DEFAULT_RATE_LIMIT - 1))
        .sendTrackEvent(API_ENDPOINTS_ERRORED_RESPONSE, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldSendTelemetryForApiKeyIfNumberOfRequestsEqualsToDefaultRateLimit() {
    for (int numberOfRequest = 0; numberOfRequest < DEFAULT_RATE_LIMIT; numberOfRequest++) {
      filter.filter(containerRequestContext, containerResponseContext);
    }

    verify(telemetryReporter, times(DEFAULT_RATE_LIMIT))
        .sendTrackEvent(API_ENDPOINTS_ERRORED_RESPONSE, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldNotThrowExceptionIfNumberOfRequestsMoreThanDefaultRateLimit() {
    for (int numberOfRequest = 0; numberOfRequest < DEFAULT_RATE_LIMIT + 1; numberOfRequest++) {
      filter.filter(containerRequestContext, containerResponseContext);
    }

    verify(telemetryReporter, times(DEFAULT_RATE_LIMIT))
        .sendTrackEvent(API_ENDPOINTS_ERRORED_RESPONSE, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = RICHA)
  @Category(UnitTests.class)
  public void shouldBeAbleToOverrideDefaultRateLimit() {
    RateLimiterConfig overriddenRateLimiterConfig = RateLimiterConfig.custom()
                                                        .limitForPeriod(CUSTOM_REQUEST_LIMIT)
                                                        .limitRefreshPeriod(Duration.ofMinutes(1))
                                                        .build();
    filter = new APIAuthTelemetryResponseFilter(telemetryReporter, overriddenRateLimiterConfig);

    for (int numberOfRequest = 0; numberOfRequest < CUSTOM_REQUEST_LIMIT + 1; numberOfRequest++) {
      filter.filter(containerRequestContext, containerResponseContext);
    }

    verify(telemetryReporter, times(CUSTOM_REQUEST_LIMIT))
        .sendTrackEvent(API_ENDPOINTS_ERRORED_RESPONSE, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }
}