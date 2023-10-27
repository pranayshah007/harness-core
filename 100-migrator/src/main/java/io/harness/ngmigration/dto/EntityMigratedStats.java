/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMigratedStats {
  private int successfullyMigrated;
  private int alreadyMigrated;

  public void incrementSuccessfullyMigrated() {
    this.successfullyMigrated += 1;
  }

  public void incrementAlreadyMigrated() {
    this.alreadyMigrated += 1;
  }
}
