/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.checks.impl;

import io.harness.ModuleType;
import io.harness.licensing.EditionAction;
import io.harness.licensing.checks.LicenseEditionChecker;
import io.harness.licensing.checks.ModuleLicenseState;
import io.harness.licensing.entities.modules.ModuleLicense;

import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class FreeChecker implements LicenseEditionChecker {
  @Override
  public void preCheck(ModuleLicense moduleLicense, List<ModuleLicense> currentLicenses, EditionAction targetAction) {
    // Free specific check
  }

  @Override
  public Set<EditionAction> getEditionActions(ModuleLicenseState currentModuleState, ModuleType moduleType,
      Map<ModuleType, ModuleLicense> lastExpiredActiveLicenseMap) {
    Set<EditionAction> result = new HashSet<>();

    if (ModuleLicenseState.NO_LICENSE.equals(currentModuleState)) {
      result.add(EditionAction.START_FREE);
    }
    return result;
  }
}
