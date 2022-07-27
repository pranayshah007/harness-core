/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.rollback;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class RollbackStrategyTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFromRepairActionCode() {
    List<RepairActionCode> validRepairActionCodes =
        Arrays.asList(RepairActionCode.STAGE_ROLLBACK, RepairActionCode.STEP_GROUP_ROLLBACK, RepairActionCode.UNKNOWN);
    for (RepairActionCode value : RepairActionCode.values()) {
      if (!validRepairActionCodes.contains(value)) {
        assertThat(RollbackStrategy.fromRepairActionCode(value)).isNull();
      }
    }

    assertThat(RollbackStrategy.fromRepairActionCode(RepairActionCode.STAGE_ROLLBACK))
        .isEqualTo(RollbackStrategy.STAGE_ROLLBACK);
    assertThat(RollbackStrategy.fromRepairActionCode(RepairActionCode.STEP_GROUP_ROLLBACK))
        .isEqualTo(RollbackStrategy.STEP_GROUP_ROLLBACK);
    assertThat(RollbackStrategy.fromRepairActionCode(RepairActionCode.UNKNOWN)).isEqualTo(RollbackStrategy.UNKNOWN);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFromYamlName() {
    assertThat(RollbackStrategy.fromYamlName("StageRollback")).isEqualTo(RollbackStrategy.STAGE_ROLLBACK);
    assertThat(RollbackStrategy.fromYamlName("StepGroupRollback")).isEqualTo(RollbackStrategy.STEP_GROUP_ROLLBACK);
    assertThat(RollbackStrategy.fromYamlName("Unknown")).isEqualTo(RollbackStrategy.UNKNOWN);
    assertThat(RollbackStrategy.fromYamlName("CUSTOM_FAILURE")).isNull();
  }
}
