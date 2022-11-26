/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.interfaces.clients.local;

import static io.harness.licensing.LicenseConstant.UNLIMITED;
import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;

import io.harness.exception.UnsupportedOperationException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.CVModuleLicenseDTO;
import io.harness.licensing.beans.modules.CVModuleLicenseDTO.CVModuleLicenseDTOBuilder;
import io.harness.licensing.interfaces.clients.CVModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class CVLocalClient implements CVModuleLicenseClient {
  private static final int FREE_TRIAL_SERVICES = 5;
  private static final int TEAM_TRIAL_SERVICES = 100;
  @Override
  public CVModuleLicenseDTO createTrialLicense(Edition edition, String accountId) {
    long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
    long currentTime = Instant.now().toEpochMilli();

    CVModuleLicenseDTOBuilder<?, ?> builder = CVModuleLicenseDTO.builder()
                                                  .startTime(currentTime)
                                                  .expiryTime(expiryTime)
                                                  .selfService(true)
                                                  .status(LicenseStatus.ACTIVE);

    switch (edition) {
      case ENTERPRISE:
        return builder.licenseType(LicenseType.TRIAL).numberOfServices(Integer.valueOf(UNLIMITED)).build();
      case TEAM:
        return builder.licenseType(LicenseType.TRIAL).numberOfServices(TEAM_TRIAL_SERVICES).build();
      case FREE:
        return builder.expiryTime(Long.MAX_VALUE).numberOfServices(FREE_TRIAL_SERVICES).build();
      default:
        throw new UnsupportedOperationException("Requested edition is not supported");
    }
  }
}
