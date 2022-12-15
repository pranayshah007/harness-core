/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.interfaces.clients.local;

import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;

import io.harness.exception.UnsupportedOperationException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.CFModuleLicenseDTO;
import io.harness.licensing.beans.modules.CFModuleLicenseDTO.CFModuleLicenseDTOBuilder;
import io.harness.licensing.interfaces.clients.CFModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class CFLocalClient implements CFModuleLicenseClient {
  private static final int ENTERPRISE_TRIAL_FEATURE_FLAG_UNITS = 50;
  private static final long ENTERPRISE_TRIAL_CLIENT_MAUS = 1000000;

  private static final int TEAM_TRIAL_FEATURE_FLAG_UNITS = 50;
  private static final long TEAM_TRIAL_CLIENT_MAUS = 1000000;

  private static final int FREE_FEATURE_FLAG_UNITS = 2;
  private static final long FREE_CLIENT_MAUS = 25000;

  @Override
  public CFModuleLicenseDTO createTrialLicense(Edition edition, String accountId) {
    long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
    long currentTime = Instant.now().toEpochMilli();

    CFModuleLicenseDTOBuilder<?, ?> builder = CFModuleLicenseDTO.builder()
                                                  .startTime(currentTime)
                                                  .selfService(true)
                                                  .expiryTime(expiryTime)
                                                  .status(LicenseStatus.ACTIVE);

    switch (edition) {
      case ENTERPRISE:
        return builder.numberOfUsers(ENTERPRISE_TRIAL_FEATURE_FLAG_UNITS)
            .numberOfClientMAUs(ENTERPRISE_TRIAL_CLIENT_MAUS)
            .licenseType(LicenseType.TRIAL)
            .build();
      case TEAM:
        return builder.numberOfUsers(TEAM_TRIAL_FEATURE_FLAG_UNITS)
            .numberOfClientMAUs(TEAM_TRIAL_CLIENT_MAUS)
            .licenseType(LicenseType.TRIAL)
            .build();
      case FREE:
        return builder.numberOfUsers(FREE_FEATURE_FLAG_UNITS)
            .numberOfClientMAUs(FREE_CLIENT_MAUS)
            .expiryTime(Long.MAX_VALUE)
            .build();
      default:
        throw new UnsupportedOperationException("Requested edition is not supported");
    }
  }
}
