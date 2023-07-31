/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.license;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.CILicenseSummaryDTO;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;

@OwnedBy(STO)
public class STOLicenseNoopServiceImpl implements CILicenseService {
  @Override
  public LicensesWithSummaryDTO getLicenseSummary(String accountId) {
    return CILicenseSummaryDTO.builder()
        .edition(Edition.ENTERPRISE)
        .licenseType(LicenseType.PAID)
        .maxExpiryTime(Long.MAX_VALUE)
        .moduleType(ModuleType.CI)
        .totalDevelopers(-1) // unlimited
        .build();
  }
}
