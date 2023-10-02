/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.CustomDeploymentOutcomeMetadata;
import io.harness.delegate.beans.instancesync.DeploymentOutcomeMetadata;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.dtos.InstanceDTO;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
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
        .instanceName(deploymentPackageInstanceInfoDTO.getInstanceName())
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
        .instanceName(customDeploymentServerInstanceInfo.getInstanceName())
        .infrastructureKey(((CustomDeploymentServerInstanceInfo) serverInstanceInfo).getInfrastructureKey())
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
    return CustomDeploymentNGDeploymentInfoDTO.builder()
        .infratructureKey(infrastructureOutcome.getInfrastructureKey())
        .build();
  }

  @Override
  public InstanceDTO updateInstance(InstanceDTO instanceDTO, InstanceInfoDTO instanceInfoFromServer) {
    instanceDTO.setInstanceInfoDTO(instanceInfoFromServer);
    instanceDTO.setLastDeployedAt(System.currentTimeMillis());
    return instanceDTO;
  }

  @Override
  public DeploymentInfoDTO updateDeploymentInfoDTO(
      DeploymentInfoDTO deploymentInfoDTO, DeploymentOutcomeMetadata deploymentOutcomeMetadata) {
    if (isNull(deploymentOutcomeMetadata)) {
      return deploymentInfoDTO;
    }
    if (!(deploymentInfoDTO instanceof CustomDeploymentNGDeploymentInfoDTO)) {
      throw new InvalidArgumentsException(
          Pair.of("deploymentInfoDTO", "Must be instance of CustomDeploymentNGDeploymentInfoDTO"));
    }

    if (!(deploymentOutcomeMetadata instanceof CustomDeploymentOutcomeMetadata)) {
      throw new InvalidArgumentsException(
          Pair.of("deploymentOutcomeMetadata", "Must be instance of CustomDeploymentOutcomeMetadata"));
    }
    CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO =
        (CustomDeploymentNGDeploymentInfoDTO) deploymentInfoDTO;
    CustomDeploymentOutcomeMetadata customDeploymentOutcomeMetadata =
        (CustomDeploymentOutcomeMetadata) deploymentOutcomeMetadata;

    customDeploymentNGDeploymentInfoDTO.setInstanceFetchScript(
        customDeploymentOutcomeMetadata.getInstanceFetchScript());
    customDeploymentNGDeploymentInfoDTO.setTags(customDeploymentOutcomeMetadata.getDelegateSelectors());
    return customDeploymentNGDeploymentInfoDTO;
  }
}
