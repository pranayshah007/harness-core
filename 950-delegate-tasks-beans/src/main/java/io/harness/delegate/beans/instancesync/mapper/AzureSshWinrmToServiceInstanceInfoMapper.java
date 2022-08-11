/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class AzureSshWinrmToServiceInstanceInfoMapper {
  public ServerInstanceInfo toServerInstanceInfo(String serviceType, String host, String infrastructureKey) {
    return AzureSshWinrmServerInstanceInfo.builder()
        .serviceType(serviceType)
        .host(host)
        .infrastructureKey(infrastructureKey)
        .build();
  }
}
