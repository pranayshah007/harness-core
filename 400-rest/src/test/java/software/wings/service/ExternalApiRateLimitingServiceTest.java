/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.threading.Morpheus.sleep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.ExternalApiRateLimitingService;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ExternalApiRateLimitingServiceTest extends WingsBaseTest {
  private static final double ERROR_THRESHOLD = 1;
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  @Mock private LimitConfigurationService limitConfigurationService;
  @Inject private ExternalApiRateLimitingService service;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllowedRequests() {
    StaticLimit staticLimit = new StaticLimit(60);
    ConfiguredLimit limit = new ConfiguredLimit<>(GLOBAL_ACCOUNT_ID, staticLimit, ActionType.MAX_QPM_PER_MANAGER);

    when(limitConfigurationService.getOrDefault(GLOBAL_ACCOUNT_ID, ActionType.MAX_QPM_PER_MANAGER)).thenReturn(limit);
    String key = "abcd";
    double numAllowed = 0;
    long currentTime = System.currentTimeMillis();
    long endTime = currentTime + 3000; // 3 second
    while (System.currentTimeMillis() < endTime) {
      if (!service.rateLimitRequest(key)) {
        numAllowed++;
      }

      // Introducing a sleep to avoid spinning the CPU
      sleep(Duration.ofMillis(20));
    }

    // We are running the test for 3 seconds. So max allowed requests = QPM / (60 / 3) -> QPM / 20
    assertThat(numAllowed).isLessThanOrEqualTo(service.getMaxQPMPerManager(key) / 20 + ERROR_THRESHOLD);
  }
}
