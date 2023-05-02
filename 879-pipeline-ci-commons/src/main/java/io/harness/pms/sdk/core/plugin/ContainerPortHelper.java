/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils.getKubernetesStandardPodName;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.container.exception.ContainerStepExecutionException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerPortHelper {
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public Integer getPort(Ambiance ambiance, String stepIdentifier) {
    // Ports are assigned in lite engine step
    ContainerPortDetails containerPortDetails = (ContainerPortDetails) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(PORT_DETAILS));

    List<Integer> ports = containerPortDetails.getPortDetails().get(getKubernetesStandardPodName(stepIdentifier));

    if (ports.size() != 1) {
      throw new ContainerStepExecutionException(format("Step [%s] should map to single port", stepIdentifier));
    }

    return ports.get(0);
  }
}
