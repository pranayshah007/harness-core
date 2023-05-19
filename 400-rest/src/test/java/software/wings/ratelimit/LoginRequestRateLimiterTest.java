/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.ratelimit;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.lib.RateBasedLimit;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LoginRequestRateLimiterTest extends WingsBaseTest {
  @Mock private LimitConfigurationService limitConfigurationService;
  @Inject @InjectMocks private LoginRequestRateLimiter loginRequestRateLimiter;

  @Before
  public void setUp() {
    loginRequestRateLimiter = new LoginRequestRateLimiter(limitConfigurationService);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testOverGlobalRateLimiter() {
    boolean overRateLimit = false;
    int count = 15;
    String remoteHost = "remote_host";
    for (int i = 0; i < count; i++) {
      when(limitConfigurationService.getOrDefault(anyString(), eq(ActionType.LOGIN_REQUEST_TASK))).thenReturn(null);
      overRateLimit = loginRequestRateLimiter.isOverRateLimit(remoteHost);
    }
    assertThat(overRateLimit).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testOverGlobalRateLimiter() {
    boolean overRateLimit = false;
    int count = 400;
    String remoteHost = "remote_host1";

    when(limitConfigurationService.getOrDefault(anyString(), eq(ActionType.LOGIN_REQUEST_TASK))).thenReturn(null);

    for (int i = 0; i < count; i++) {
      overRateLimit = loginRequestRateLimiter.isOverRateLimit(remoteHost);
    }
    assertThat(overRateLimit).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testGlobalRateLimiter() {
    boolean overRateLimit = false;
    int globalRateLimit = 300;
    String remoteHost = "remote_host2";
    int count = 400;

    when(limitConfigurationService.getOrDefault(anyString(), eq(ActionType.LOGIN_REQUEST_TASK)))
        .thenReturn(getConfiguredLimit(remoteHost, globalRateLimit));

    for (int i = 0; i < count; i++) {
      overRateLimit = loginRequestRateLimiter.isOverRateLimit(remoteHost);
    }
    assertThat(overRateLimit).isTrue();
  }

  private ConfiguredLimit getConfiguredLimit(String accountId, int requestCountLimit) {
    RateBasedLimit rateLimit = new RateLimit(requestCountLimit, 1, TimeUnit.MINUTES);
    return new ConfiguredLimit<>(accountId, rateLimit, ActionType.LOGIN_REQUEST_TASK);
  }
}
