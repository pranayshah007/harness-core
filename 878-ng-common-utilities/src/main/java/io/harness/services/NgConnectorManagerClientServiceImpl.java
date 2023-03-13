/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.services;

import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.client.NgConnectorManagerClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class NgConnectorManagerClientServiceImpl implements NgConnectorManagerClientService {
  private final NgConnectorManagerClient ngConnectorManagerClient;

  @Inject
  public NgConnectorManagerClientServiceImpl(NgConnectorManagerClient ngConnectorManagerClient) {
    this.ngConnectorManagerClient = ngConnectorManagerClient;
  }

  @Override
  public boolean isHarnessSupportUser(String userId) {
    return getResponse(ngConnectorManagerClient.isHarnessSupportUser(userId));
  }
}
