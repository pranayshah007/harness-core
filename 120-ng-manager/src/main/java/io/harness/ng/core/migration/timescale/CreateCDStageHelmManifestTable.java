/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class CreateCDStageHelmManifestTable extends NGAbstractTimeScaleMigration {
  @Override
  public String getFileName() {
    return "timescale/create_cd_stage_helm_manifest_table.sql";
  }
}
