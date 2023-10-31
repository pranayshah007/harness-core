/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.security;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

@OwnedBy(HarnessTeam.CDC)
public class NgManagerSourcePrincipalGuard implements AutoCloseable {
  private final Principal initialSourcePrincipal;
  public NgManagerSourcePrincipalGuard() {
    initialSourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    // sets the ServicePrincipal of ng manager
    // should be used with where User Principal/RBAC is not required. E.g. Variable creation
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.NG_MANAGER.getServiceId()));
  }

  public NgManagerSourcePrincipalGuard(SetupMetadata setupMetadata) {
    initialSourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    // sets the principal using SetupMetadata passed
    if (setupMetadata != null) {
      SourcePrincipalContextBuilder.setSourcePrincipal(PmsSecurityContextGuardUtils.getPrincipal(
          setupMetadata.getAccountId(), setupMetadata.getPrincipalInfo(), setupMetadata.getTriggeredInfo()));
    }
  }

  @Override
  public void close() throws Exception {
    SourcePrincipalContextBuilder.setSourcePrincipal(initialSourcePrincipal);
  }
}