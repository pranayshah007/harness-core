package io.harness.licensing.interfaces.clients.local;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnsupportedOperationException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.SEIModuleLicenseDTO;
import io.harness.licensing.interfaces.clients.SEIModuleLicenseClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.harness.licensing.LicenseConstant.UNLIMITED;
import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;

@OwnedBy(HarnessTeam.SEI)
public class SEILocalClient implements SEIModuleLicenseClient {

    @Override
    public SEIModuleLicenseDTO createTrialLicense(Edition edition, String accountid) {
        long expiryTime = Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli();
        long currentTime = Instant.now().toEpochMilli();

        SEIModuleLicenseDTO.SEIModuleLicenseDTOBuilder<?, ?> builder =
                SEIModuleLicenseDTO.builder().startTime(currentTime).expiryTime(expiryTime).status(LicenseStatus.ACTIVE);

        // Note: SEI initially will only support Enterprise Trials.
        if (edition == Edition.ENTERPRISE) {
            return builder.licenseType(LicenseType.TRIAL).numberOfContributors(Integer.valueOf(UNLIMITED)).build();
        } else {
            throw new UnsupportedOperationException("Requested edition is not supported");
        }
    }
}
