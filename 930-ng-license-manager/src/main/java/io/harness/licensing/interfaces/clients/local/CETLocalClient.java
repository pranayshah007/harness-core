/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.interfaces.clients.local;

import io.harness.exception.UnsupportedOperationException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.CETModuleLicenseDTO;
import io.harness.licensing.interfaces.clients.CETModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;

public class CETLocalClient implements CETModuleLicenseClient {
    private static final int FREE_TRIAL_AGENTS = 5;

    private static final int TEAM_TRIAL_AGENTS = 10;

    private static final int ENTERPRISE_TRIAL_AGENTS = 30;


    @Override
    public CETModuleLicenseDTO createTrialLicense(Edition edition, String accountId) {
        long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
        long currentTime = Instant.now().toEpochMilli();

        CETModuleLicenseDTO.CETModuleLicenseDTOBuilder<?, ?> builder = CETModuleLicenseDTO.builder()
                .startTime(currentTime)
                .expiryTime(expiryTime)
                .selfService(true)
                .status(LicenseStatus.ACTIVE);

        switch (edition) {
            case ENTERPRISE:
                return builder.licenseType(LicenseType.TRIAL).numberOfAgents(ENTERPRISE_TRIAL_AGENTS).build();
            case TEAM:
                return builder.licenseType(LicenseType.TRIAL).numberOfAgents(TEAM_TRIAL_AGENTS).build();
            case FREE:
                return builder.expiryTime(Long.MAX_VALUE).numberOfAgents(FREE_TRIAL_AGENTS).build();
            default:
                throw new UnsupportedOperationException("Requested edition is not supported");
        }
    }
}
