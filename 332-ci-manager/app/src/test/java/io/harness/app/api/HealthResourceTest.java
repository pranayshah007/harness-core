/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.api;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.app.resources.HealthResourceImpl;
import io.harness.category.element.UnitTests;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.rule.Owner;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HealthResourceTest extends CategoryTest {
  private HealthResourceImpl healthResource;
  private HealthService healthService;
  private MockedStatic<MaintenanceController> aStatic;
  private ThreadDeadlockHealthCheck threadDeadlockHealthCheck;

  @Before
  public void setup() {
    healthService = mock(HealthService.class);
    healthResource = new HealthResourceImpl(healthService);
    aStatic = mockStatic(MaintenanceController.class);
    threadDeadlockHealthCheck = mock(ThreadDeadlockHealthCheck.class);
    on(healthResource).set("threadDeadlockHealthCheck", threadDeadlockHealthCheck);
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGet_success() throws Exception {
    when(MaintenanceController.getMaintenanceFlag()).thenAnswer(value -> false);
    when(healthService.check()).thenReturn(HealthCheck.Result.healthy());
    String healthResponse = healthResource.get().getResource();
    Assertions.assertThat(healthResponse).isNotNull();
    Assertions.assertThat(healthResponse).isEqualTo("healthy");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGet_failure_due_to_monitor() throws Exception {
    when(healthService.check()).thenReturn(HealthCheck.Result.unhealthy("DB down"));
    try {
      healthResource.get();
      fail("Execution should not reach here");
    } catch (HealthException healthException) {
      // not required
    }
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testLiveness_success() {
    when(threadDeadlockHealthCheck.execute()).thenReturn(HealthCheck.Result.healthy("healthy"));
    String healthResponse = healthResource.doLivenessCheck().getResource();
    Assertions.assertThat(healthResponse).isNotNull();
    Assertions.assertThat(healthResponse).isEqualTo("live");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testLiveness_failure() {
    when(threadDeadlockHealthCheck.execute()).thenReturn(HealthCheck.Result.unhealthy("unhealthy"));
    try {
      healthResource.doLivenessCheck();
      fail("Execution should not reach here");
    } catch (HealthException healthException) {
      // not required
    }
  }
}
