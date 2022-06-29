/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.govern.Switch.unhandled;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.ci.integrationstage.DockerInitializeTaskParamsBuilder;
import io.harness.ci.integrationstage.K8InitializeTaskParamsBuilder;
import io.harness.ci.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class BuildSetupUtils {
  @Inject private K8InitializeTaskParamsBuilder k8InitializeTaskParamsBuilder;
  @Inject private K8BuildSetupUtils k8BuildSetupUtils;
  @Inject private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject private DockerInitializeTaskParamsBuilder dockerInitializeTaskParamsBuilder;

  public CIInitializeTaskParams getBuildSetupTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance, String logPrefix) {
    switch (initializeStepInfo.getInfrastructure().getType()) {
      case KUBERNETES_DIRECT:
      case KUBERNETES_HOSTED:
        return k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, logPrefix);
      case VM:
        return vmInitializeTaskParamsBuilder.getVmInitializeTaskParams(initializeStepInfo, ambiance, logPrefix);
      case DOCKER:
        return dockerInitializeTaskParamsBuilder.getDockerInitializeTaskParams(initializeStepInfo, ambiance, logPrefix);
      default:
        unhandled(initializeStepInfo.getBuildJobEnvInfo().getType());
    }
    return null;
  }

  public List<ContainerDefinitionInfo> getBuildServiceContainers(InitializeStepInfo initializeStepInfo) {
    switch (initializeStepInfo.getBuildJobEnvInfo().getType()) {
      case K8:
        return k8BuildSetupUtils.getCIk8BuildServiceContainers(initializeStepInfo);
      default:
        unhandled(initializeStepInfo.getBuildJobEnvInfo().getType());
    }
    return null;
  }
}
