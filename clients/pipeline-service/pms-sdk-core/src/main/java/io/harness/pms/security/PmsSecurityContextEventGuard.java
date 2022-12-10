/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.security;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSecurityContextEventGuard implements AutoCloseable {
  public PmsSecurityContextEventGuard(Ambiance ambiance) {
    if (ambiance != null) {
      io.harness.security.dto.Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
      if (principal != null) {
        SecurityContextBuilder.setContext(principal);
        SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      }
    }
  }

  @Override
  public void close() throws Exception {
    SecurityContextBuilder.unsetCompleteContext();
  }
}
