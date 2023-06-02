/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.interfaces.clients;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.IACMModuleLicenseDTO;
@OwnedBy(HarnessTeam.IACM)
public interface IACMModuleLicenseClient extends ModuleLicenseClient<IACMModuleLicenseDTO> {
  @Override IACMModuleLicenseDTO createTrialLicense(Edition edition, String accountId);
}
