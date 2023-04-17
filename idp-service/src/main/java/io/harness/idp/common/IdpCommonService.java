/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.client.NgConnectorManagerClient;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.security.SecurityContextBuilder;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpCommonService {
  @Inject NgConnectorManagerClient ngConnectorManagerClient;

  public void checkUserAuthorization() {
    String userId = SecurityContextBuilder.getPrincipal().getName();
    boolean isAuthorized = getResponse(ngConnectorManagerClient.isHarnessSupportUser(userId));
    if (!isAuthorized) {
      String errorMessage = String.format("User : %s not allowed to do action on IDP module", userId);
      log.error(errorMessage);
      throw new AccessDeniedException(errorMessage, WingsException.USER);
    }
  }
}
