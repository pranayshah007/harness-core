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
import io.harness.licensing.beans.modules.CETModuleLicenseDTO;
import io.harness.licensing.beans.modules.CETModuleLicenseDTO.CETModuleLicenseDTOBuilder;
import io.harness.licensing.interfaces.clients.CETModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@OwnedBy(HarnessTeam.CET)
public class CETLocalClient implements CETModuleLicenseClient {
  @Override
  public CETModuleLicenseDTO createTrialLicense(Edition edition, String accountId) {
    long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
    long currentTime = Instant.now().toEpochMilli();

    CETModuleLicenseDTOBuilder<?, ?> builder =
        CETModuleLicenseDTO.builder().startTime(currentTime).expiryTime(expiryTime).status(LicenseStatus.ACTIVE);

    // Note: CET initially will only support Enterprise Trials.
    if (edition == Edition.ENTERPRISE) {
      return builder.licenseType(LicenseType.TRIAL).numberOfAgents(Integer.valueOf(UNLIMITED)).build();
    } else {
      throw new UnsupportedOperationException("Requested edition is not supported");
    }
  }
}
