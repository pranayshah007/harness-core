package io.harness.licensing.interfaces.clients;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.SEIModuleLicenseDTO;

@OwnedBy(HarnessTeam.SEI)
public interface SEIModuleLicenseClient extends ModuleLicenseClient<SEIModuleLicenseDTO>{
    @Override
    SEIModuleLicenseDTO createTrialLicense(Edition edition, String accountId);
}
