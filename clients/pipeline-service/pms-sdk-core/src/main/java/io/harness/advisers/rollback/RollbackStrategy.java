/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.advisers.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.commons.RepairActionCode;

@OwnedBy(CDC)
public enum RollbackStrategy {
  STAGE_ROLLBACK("StageRollback"),
  STEP_GROUP_ROLLBACK("StepGroupRollback"),
  UNKNOWN("Unknown");

  String yamlName;

  RollbackStrategy(String yamlName) {
    this.yamlName = yamlName;
  }

  public static RollbackStrategy fromRepairActionCode(RepairActionCode repairActionCode) {
    for (RollbackStrategy value : RollbackStrategy.values()) {
      if (value.name().equals(repairActionCode.name())) {
        return value;
      }
    }
    return null;
  }

  public static RollbackStrategy fromYamlName(String yamlName) {
    for (RollbackStrategy value : RollbackStrategy.values()) {
      if (value.yamlName.equals(yamlName)) {
        return value;
      }
    }
    return null;
  }
}
