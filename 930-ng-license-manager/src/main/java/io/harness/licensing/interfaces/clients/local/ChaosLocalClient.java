/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.interfaces.clients.local;

import static io.harness.licensing.LicenseConstant.UNLIMITED;
import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;

import io.harness.exception.UnsupportedOperationException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ChaosModuleLicenseDTO;
import io.harness.licensing.beans.modules.ChaosModuleLicenseDTO.ChaosModuleLicenseDTOBuilder;
import io.harness.licensing.interfaces.clients.ChaosModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ChaosLocalClient implements ChaosModuleLicenseClient {
  private static final int ENTERPRISE_TRIAL_CHAOS_SCENARIO_RUN = 200;
  private static final int TEAM_TRIAL_CHAOS_SCENARIO_RUN = 100;
  private static final int FREE_TRIAL_CHAOS_SCENARIO_RUN = 60;

  private static final int FREE_TRIAL_CHAOS_DELEGATES = 10;

  @Override
  public ChaosModuleLicenseDTO createTrialLicense(Edition edition, String accountId) {
    long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
    long currentTime = Instant.now().toEpochMilli();

    ChaosModuleLicenseDTOBuilder<?, ?> builder =
        ChaosModuleLicenseDTO.builder().startTime(currentTime).expiryTime(expiryTime).status(LicenseStatus.ACTIVE);

    switch (edition) {
      case ENTERPRISE:
        return builder.totalChaosScenarioRun(ENTERPRISE_TRIAL_CHAOS_SCENARIO_RUN)
            .totalChaosDelegates(Integer.valueOf(UNLIMITED))
            .licenseType(LicenseType.TRIAL)
            .build();
      case TEAM:
        return builder.totalChaosScenarioRun(TEAM_TRIAL_CHAOS_SCENARIO_RUN)
            .totalChaosDelegates(Integer.valueOf(UNLIMITED))
            .licenseType(LicenseType.TRIAL)
            .build();
      case FREE:
        return builder.totalChaosScenarioRun(FREE_TRIAL_CHAOS_SCENARIO_RUN)
            .totalChaosDelegates(FREE_TRIAL_CHAOS_DELEGATES)
            .expiryTime(Long.MAX_VALUE)
            .build();
      default:
        throw new UnsupportedOperationException("Requested edition is not supported");
    }
  }
}
