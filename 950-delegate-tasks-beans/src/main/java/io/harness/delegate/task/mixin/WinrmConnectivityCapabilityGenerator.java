/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.mixin;

import io.harness.delegate.beans.executioncapability.WinrmConnectivityExecutionCapability;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class WinrmConnectivityCapabilityGenerator {
  public static WinrmConnectivityExecutionCapability buildWinrmConnectivityExecutionCapability(
      WinRmInfraDelegateConfig winRmInfraDelegateConfig, boolean useWinRMKerberosUniqueCacheFile, String host) {
    return WinrmConnectivityExecutionCapability.builder()
        .useWinRMKerberosUniqueCacheFile(useWinRMKerberosUniqueCacheFile)
        .winRmInfraDelegateConfig(winRmInfraDelegateConfig)
        .host(host)
        .build();
  }
}
