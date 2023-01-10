/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.maintenance;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MaintenanceControllerTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testMaintenance() {
    MaintenanceController.forceMaintenance(true);
    assertThat(MaintenanceController.getMaintenanceFlag()).isTrue();

    MaintenanceController.resetForceMaintenance();
    assertThat(MaintenanceController.getMaintenanceFlag()).isFalse();

    MaintenanceController.forceMaintenance(true);
    assertThat(MaintenanceController.getMaintenanceFlag()).isTrue();

    MaintenanceController.forceMaintenance(false);
    assertThat(MaintenanceController.getMaintenanceFlag()).isFalse();
  }
}
