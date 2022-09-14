/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.dtos.deploymentinfo.CustomDeploymentNGDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.CustomDeploymentInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.CustomDeploymentInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class CustomDeploymentInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.CUSTOM_DEPLOYMENT_INSTANCE_SYNC_NG;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.CUSTOM_DEPLOYMENT_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.CUSTOM_DEPLOYMENT;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof CustomDeploymentInstanceInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("instanceInfoDTO", "Must be instance of CustomDeploymentInstanceInfoDTO"));
    }
    CustomDeploymentInstanceInfoDTO deploymentPackageInstanceInfoDTO =
        (CustomDeploymentInstanceInfoDTO) instanceInfoDTO;
    return CustomDeploymentInfrastructureDetails.builder()
        .hostname(deploymentPackageInstanceInfoDTO.getHostname())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof CustomDeploymentServerInstanceInfo)) {
      throw new InvalidArgumentsException(Pair.of("serverInstanceInfo", "Must be instance of K8sServerInstanceInfo"));
    }

    CustomDeploymentServerInstanceInfo customDeploymentServerInstanceInfo =
        (CustomDeploymentServerInstanceInfo) serverInstanceInfo;

    return CustomDeploymentInstanceInfoDTO.builder()
        .hostname(customDeploymentServerInstanceInfo.getHostName())
        .properties(customDeploymentServerInstanceInfo.getProperties())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof CustomDeploymentInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of CustomDeploymentInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof CustomDeploymentServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of CustomDeploymentServerInstanceInfo"));
    }

    CustomDeploymentServerInstanceInfo customDeploymentServerInstanceInfo =
        (CustomDeploymentServerInstanceInfo) serverInstanceInfoList.get(0);

    return CustomDeploymentNGDeploymentInfoDTO.builder()
        .instanceFetchScript(customDeploymentServerInstanceInfo.getInstanceFetchScript())
        .build();
  }
}
