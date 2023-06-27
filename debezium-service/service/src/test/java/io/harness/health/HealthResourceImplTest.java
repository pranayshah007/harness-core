/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.health;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.NoResultFoundException;
import io.harness.maintenance.MaintenanceController;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import com.codahale.metrics.health.HealthCheck;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class HealthResourceImplTest extends CategoryTest {
  @InjectMocks HealthResourceImpl healthResourceImpl;
  @Mock HealthService healthService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGet() throws Exception {
    MaintenanceController.forceMaintenance(true);
    assertThatThrownBy(() -> healthResourceImpl.get())
        .hasMessage("in maintenance mode")
        .isInstanceOf(NoResultFoundException.class);

    MaintenanceController.forceMaintenance(false);
    doReturn(HealthCheck.Result.builder().healthy().build()).when(healthService).check();
    RestResponse<String> healthyResponse = healthResourceImpl.get();
    assertThat(healthyResponse.getResource()).isEqualTo("healthy");

    HealthCheck.Result unhealthy = HealthCheck.Result.builder().unhealthy().withMessage("any").build();
    doReturn(unhealthy).when(healthService).check();
    assertThatThrownBy(() -> healthResourceImpl.get()).isInstanceOf(HealthException.class).hasMessage("any");
  }
}
