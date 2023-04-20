/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.rule.OwnerRule.PHOENIKX;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.rule.Owner;

import com.codahale.metrics.health.HealthCheck;
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
  private HealthResource healthResource;
  private HealthService healthService;
  private MockedStatic<MaintenanceController> aStatic;

  @Before
  public void setup() {
    healthService = mock(HealthService.class);
    healthResource = new HealthResource(healthService);
    aStatic = mockStatic(MaintenanceController.class);
  }

  @After
  public void cleanup() {
    aStatic.close();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGet_success() throws Exception {
    when(MaintenanceController.getMaintenanceFlag()).thenAnswer(value -> false);
    when(healthService.check()).thenReturn(HealthCheck.Result.healthy());
    String healthResponse = healthResource.doReadinessCheck().getData();
    Assertions.assertThat(healthResponse).isNotNull();
    Assertions.assertThat(healthResponse).isEqualTo("healthy");
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGet_failure_due_to_monitor() throws Exception {
    when(healthService.check()).thenReturn(HealthCheck.Result.unhealthy("DB down"));
    try {
      String healthResponse = healthResource.doReadinessCheck().getData();
      fail("Execution should not reach here");
    } catch (HealthException healthException) {
      // not required
    }
  }
}
