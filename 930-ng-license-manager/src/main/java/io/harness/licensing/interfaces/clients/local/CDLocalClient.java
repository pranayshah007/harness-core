/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.interfaces.clients.local;

import static io.harness.licensing.LicenseConstant.UNLIMITED;
import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cd.CDLicenseType;
import io.harness.exception.UnsupportedOperationException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO.CDModuleLicenseDTOBuilder;
import io.harness.licensing.interfaces.clients.CDModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@CodePulse(
    module = ProductModule.PLG, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PLG_LICENSING})
public class CDLocalClient implements CDModuleLicenseClient {
  private static final int ENTERPRISE_TRIAL_WORKLOAD = 100;
  private static final int TEAM_TRIAL_WORKLOAD = 100;
  private static final int FREE_WORKLOAD = 5;

  @Override
  public CDModuleLicenseDTO createTrialLicense(Edition edition, String accountId) {
    long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
    long currentTime = Instant.now().toEpochMilli();

    CDModuleLicenseDTOBuilder<?, ?> builder = CDModuleLicenseDTO.builder()
                                                  .cdLicenseType(CDLicenseType.SERVICES)
                                                  .startTime(currentTime)
                                                  .expiryTime(expiryTime)
                                                  .status(LicenseStatus.ACTIVE);

    switch (edition) {
      case ENTERPRISE:
        return builder.workloads(ENTERPRISE_TRIAL_WORKLOAD).licenseType(LicenseType.TRIAL).build();
      case TEAM:
        return builder.workloads(TEAM_TRIAL_WORKLOAD).licenseType(LicenseType.TRIAL).build();
      case FREE:
        return builder.workloads(FREE_WORKLOAD).expiryTime(Long.MAX_VALUE).build();
      case COMMUNITY:
        return builder.workloads(Integer.valueOf(UNLIMITED)).expiryTime(Long.MAX_VALUE).build();
      default:
        throw new UnsupportedOperationException("Requested edition is not supported");
    }
  }
}
