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
import io.harness.expression.LateBindingMap;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@AllArgsConstructor
public class AccountVariablesMigratorFunctor extends LateBindingMap {
  private CaseFormat caseFormat;

  @Override
  public synchronized Object get(Object key) {
    return "<+variable.account." + MigratorUtility.generateIdentifier((String) key, caseFormat) + ">";
  }
}
