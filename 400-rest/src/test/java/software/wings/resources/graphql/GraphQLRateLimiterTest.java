/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.graphql;

import static io.harness.rule.OwnerRule.UTKARSH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.defaults.service.DefaultLimitsService;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.lib.RateBasedLimit;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;

import com.google.inject.Inject;
import es.moki.ratelimitj.core.limiter.request.RequestRateLimiter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * @author marklu on 9/12/19
 */
@RunWith(Parameterized.class)
@Slf4j
public class GraphQLRateLimiterTest extends CategoryTest {
  @Parameter public boolean isInternalGraphQLCall;
  @Mock private LimitConfigurationService limitConfigurationService;
  @Mock private MainConfiguration mainConfiguration;

  @Inject @InjectMocks private GraphQLRateLimiter rateLimiter;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {{true}, {false}});
  }

  @Before
  public void setUp() {
    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setExternalGraphQLRateLimitPerMinute(25);
    portalConfig.setCustomDashGraphQLRateLimitPerMinute(50);
    when(mainConfiguration.getPortal()).thenReturn(portalConfig);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testAccountLevelRateLimiter() {
    String accountId = UUIDGenerator.generateUuid();

    // Get default account level rate limiter.
    RequestRateLimiter rateLimiter1 = rateLimiter.getRateLimiterForAccountInternal(accountId, isInternalGraphQLCall);
    assertThat(rateLimiter1).isNotNull();

    int callCountLimit = isInternalGraphQLCall ? DefaultLimitsService.GRAPHQL_INTERNAL_RATE_LIMIT_ACCOUNT_DEFAULT
                                               : DefaultLimitsService.GRAPHQL_EXTERNAL_RATE_LIMIT_ACCOUNT_DEFAULT;
    ActionType actionType = isInternalGraphQLCall ? ActionType.GRAPHQL_CUSTOM_DASH_CALL : ActionType.GRAPHQL_CALL;
    ConfiguredLimit configuredLimit = getConfiguredLimit(accountId, callCountLimit);
    when(limitConfigurationService.getOrDefault(eq(accountId), eq(actionType))).thenReturn(configuredLimit);

    RequestRateLimiter rateLimiter2 = rateLimiter.getRateLimiterForAccountInternal(accountId, isInternalGraphQLCall);
    assertThat(rateLimiter2).isNotNull();
    assertThat(rateLimiter2).isEqualTo(rateLimiter1);

    callCountLimit = 10;
    configuredLimit = getConfiguredLimit(accountId, callCountLimit);
    when(limitConfigurationService.getOrDefault(eq(accountId), eq(actionType))).thenReturn(configuredLimit);

    // Get cached customized account level rate limiter.
    RequestRateLimiter rateLimiter3 = rateLimiter.getRateLimiterForAccount(accountId, isInternalGraphQLCall);
    assertThat(rateLimiter3).isNotNull();
    assertThat(rateLimiter3).isNotEqualTo(rateLimiter1);

    // Get the same cached customized account level rate limiter.
    RequestRateLimiter rateLimiter4 = rateLimiter.getRateLimiterForAccount(accountId, isInternalGraphQLCall);
    assertThat(rateLimiter4).isNotNull();
    assertThat(rateLimiter4).isEqualTo(rateLimiter3);

    boolean overRateLimit = false;
    for (int i = 0; i < callCountLimit + 1; i++) {
      overRateLimit = rateLimiter.isOverApiRateLimit(accountId, isInternalGraphQLCall);
    }
    assertThat(overRateLimit).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH, intermittent = true)
  @Category(UnitTests.class)
  public void testGlobalRateLimiter() {
    // Global rate limiter should check against cross-account overall requests on global limit.
    boolean overRateLimit = false;
    int globalRateLimit = isInternalGraphQLCall ? mainConfiguration.getPortal().getCustomDashGraphQLRateLimitPerMinute()
                                                : mainConfiguration.getPortal().getExternalGraphQLRateLimitPerMinute();
    log.info("isInternalGraphQLCall: {}; Global rate limit: {}", isInternalGraphQLCall, globalRateLimit);
    // One more extra call will over the global limit
    for (int i = 0; i < globalRateLimit + 1; i++) {
      String accountId = UUIDGenerator.generateUuid();
      overRateLimit = rateLimiter.isOverApiRateLimit(accountId, isInternalGraphQLCall);
    }
    assertThat(overRateLimit).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testMixedCustDashExternalRateLimiterCallsForSameAccount() {
    boolean overExternalRateLimit = false;
    boolean overInternalRateLimit = false;
    int internalRateLimit = mainConfiguration.getPortal().getCustomDashGraphQLRateLimitPerMinute();
    int externalRateLimit = mainConfiguration.getPortal().getExternalGraphQLRateLimitPerMinute();
    String accountId = UUIDGenerator.generateUuid();

    ConfiguredLimit configuredLimit = getConfiguredLimit(accountId, 4 * internalRateLimit);
    when(limitConfigurationService.getOrDefault(eq(accountId), eq(ActionType.GRAPHQL_CUSTOM_DASH_CALL)))
        .thenReturn(configuredLimit);
    when(limitConfigurationService.getOrDefault(eq(accountId), eq(ActionType.GRAPHQL_CALL)))
        .thenReturn(configuredLimit);

    for (int i = 0; i < externalRateLimit; i++) {
      overExternalRateLimit = rateLimiter.isOverApiRateLimit(accountId, false);
      overInternalRateLimit = rateLimiter.isOverApiRateLimit(accountId, true);
    }
    assertThat(overInternalRateLimit).isFalse();
    assertThat(overExternalRateLimit).isFalse();

    // Internal rate limit is always higher than external rate limit
    for (int i = 0; i < internalRateLimit - externalRateLimit; i++) {
      overExternalRateLimit = rateLimiter.isOverApiRateLimit(accountId, false);
      overInternalRateLimit = rateLimiter.isOverApiRateLimit(accountId, true);
    }
    assertThat(overInternalRateLimit).isFalse();
    assertThat(overExternalRateLimit).isTrue();

    // One more extra call will over the limit
    overInternalRateLimit = rateLimiter.isOverApiRateLimit(accountId, true);
    assertThat(overInternalRateLimit).isTrue();
  }

  private ConfiguredLimit getConfiguredLimit(String accountId, int callCountLimit) {
    RateBasedLimit rateLimit = new RateLimit(callCountLimit, 1, TimeUnit.MINUTES);
    return new ConfiguredLimit<>(accountId, rateLimit, ActionType.GRAPHQL_CALL);
  }
}
