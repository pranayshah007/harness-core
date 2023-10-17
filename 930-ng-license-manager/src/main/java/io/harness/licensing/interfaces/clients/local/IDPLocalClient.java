/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.interfaces.clients.local;

import static io.harness.licensing.LicenseConstant.UNLIMITED;
import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnsupportedOperationException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.IDPModuleLicenseDTO;
import io.harness.licensing.beans.modules.IDPModuleLicenseDTO.IDPModuleLicenseDTOBuilder;
import io.harness.licensing.interfaces.clients.IDPModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@OwnedBy(HarnessTeam.IDP)
public class IDPLocalClient implements IDPModuleLicenseClient {
  private static final int ENTERPRISE_TRIAL_DEVELOPERS = 300;
  private static final int TEAM_TRIAL_DEVELOPERS = 300;

  @Override
  public IDPModuleLicenseDTO createTrialLicense(Edition edition, String accountId) {
    long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
    long currentTime = Instant.now().toEpochMilli();

    IDPModuleLicenseDTOBuilder<?, ?> builder =
        IDPModuleLicenseDTO.builder().startTime(currentTime).expiryTime(expiryTime).status(LicenseStatus.ACTIVE);

    switch (edition) {
      case ENTERPRISE:
        return builder.numberOfDevelopers(ENTERPRISE_TRIAL_DEVELOPERS).licenseType(LicenseType.TRIAL).build();
      case TEAM:
        return builder.numberOfDevelopers(TEAM_TRIAL_DEVELOPERS).licenseType(LicenseType.TRIAL).build();
      case FREE:
        return builder.numberOfDevelopers(UNLIMITED).expiryTime(Long.MAX_VALUE).build();
      default:
        throw new UnsupportedOperationException("Requested edition is not supported");
    }
  }
}
