/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.security.PrincipalHelper;
import io.harness.security.dto.Principal;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class TriggeredByHelper {
  @Inject private CurrentUserHelper currentUserHelper;

  public TriggeredBy getFromSecurityContext() {
    Principal principal = currentUserHelper.getPrincipalFromSecurityContext();
    return TriggeredBy.newBuilder()
        .setUuid(emptyIfNull(PrincipalHelper.getUuid(principal)))
        .setIdentifier(emptyIfNull(PrincipalHelper.getUsername(principal)))
        .putExtraInfo("email", emptyIfNull(PrincipalHelper.getEmail(principal)))
        .build();
  }
}
