/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.pdcconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.WinrmConnectivityExecutionCapability;
import io.harness.delegate.task.mixin.WinrmConnectivityCapabilityGenerator;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class WinrmCapabilityHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      WinRmInfraDelegateConfig winRmInfraDelegateConfig, boolean useWinRMKerberosUniqueCacheFile, String host) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();

    WinrmConnectivityExecutionCapability winrmConnectivityExecutionCapability =
        WinrmConnectivityCapabilityGenerator.buildWinrmConnectivityExecutionCapability(
            winRmInfraDelegateConfig, useWinRMKerberosUniqueCacheFile, host);
    capabilityList.add(winrmConnectivityExecutionCapability);
    return capabilityList;
  }
}
