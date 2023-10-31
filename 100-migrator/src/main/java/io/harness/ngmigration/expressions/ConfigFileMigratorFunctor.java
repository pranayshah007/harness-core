/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class ConfigFileMigratorFunctor implements ExpressionFunctor {
  private CaseFormat caseFormat;

  public ConfigFileMigratorFunctor(CaseFormat caseFormat) {
    this.caseFormat = caseFormat;
  }

  public Object getAsBase64(String name) {
    return "<+configFile.getAsBase64(\"" + MigratorUtility.generateIdentifier(name, caseFormat) + "\")>";
  }

  public Object getAsString(String name) {
    return "<+configFile.getAsString(\"" + MigratorUtility.generateIdentifier(name, caseFormat) + "\")>";
  }
}
