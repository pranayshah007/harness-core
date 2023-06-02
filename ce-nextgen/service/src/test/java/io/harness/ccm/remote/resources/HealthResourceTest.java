/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.rule.Owner;

import com.codahale.metrics.health.HealthCheck;
import com.google.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CE)
@RunWith(MockitoJUnitRunner.class)
public class HealthResourceTest extends CategoryTest {
  @Mock private HealthService healthService;
  MockedStatic<MaintenanceController> maintenanceControllerMockedStatic;
  @Inject @InjectMocks private HealthResource healthResource;

  @Before
  public void setup() {
    maintenanceControllerMockedStatic = Mockito.mockStatic(MaintenanceController.class);
  }

  @After
  public void cleanup() {
    maintenanceControllerMockedStatic.close();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGet_success() throws Exception {
    maintenanceControllerMockedStatic.when(() -> MaintenanceController.getMaintenanceFlag()).thenReturn(false);
    when(healthService.check()).thenReturn(HealthCheck.Result.healthy());

    String healthResponse = healthResource.get().getData();
    assertThat(healthResponse).isNotNull();
    assertThat(healthResponse).isEqualTo(HealthService.HEALTHY);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGet_failure_due_to_monitor() throws Exception {
    when(healthService.check()).thenReturn(HealthCheck.Result.unhealthy("DB down"));

    assertThatThrownBy(() -> healthResource.get().getData()).isExactlyInstanceOf(HealthException.class);
  }
}
