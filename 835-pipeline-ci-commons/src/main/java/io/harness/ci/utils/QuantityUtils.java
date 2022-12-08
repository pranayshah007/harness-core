/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.quantity.CpuQuantity;
import io.harness.beans.quantity.StorageQuantity;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;

@OwnedBy(HarnessTeam.CI)
public class QuantityUtils {
  public static Integer getStorageQuantityValueInUnit(String memoryQuantityString, StorageQuantityUnit targetUnit) {
    StorageQuantity storageQuantity = StorageQuantity.fromString(memoryQuantityString);
    double numeric = Double.parseDouble(storageQuantity.getNumericValue());
    double multiplier = Math.pow(storageQuantity.getUnit().getBase(), storageQuantity.getUnit().getExponent());
    double targetUnitMultiplier = Math.pow(targetUnit.getBase(), targetUnit.getExponent());
    return Math.toIntExact((long) Math.ceil(numeric * (multiplier / targetUnitMultiplier)));
  }

  public static Integer getCpuQuantityValueInUnit(String cpuQuantityString, DecimalQuantityUnit targetUnit) {
    CpuQuantity cpuQuantity = CpuQuantity.fromString(cpuQuantityString);
    double numeric = Double.parseDouble(cpuQuantity.getNumericValue());
    double multiplier = Math.pow(cpuQuantity.getUnit().getBase(), cpuQuantity.getUnit().getExponent());
    double targetUnitMultiplier = Math.pow(targetUnit.getBase(), targetUnit.getExponent());
    return Math.toIntExact(Math.round(numeric * (multiplier / targetUnitMultiplier)));
  }
}
