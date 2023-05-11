/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.K8sInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.K8sDeploymentReleaseDetails;

import com.google.inject.Singleton;
import com.google.protobuf.Any;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class K8sInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.K8S_INSTANCE_SYNC;
  }

  @Override
  public String getPerpetualTaskV2Type() {
    return PerpetualTaskType.K8S_INSTANCE_SYNC_V2;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.K8S_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.KUBERNETES_DIRECT;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (!(instanceInfoDTO instanceof K8sInstanceInfoDTO)) {
      throw new InvalidArgumentsException(Pair.of("instanceInfoDTO", "Must be instance of K8sInstanceInfoDTO"));
    }
    K8sInstanceInfoDTO k8sInstanceInfoDTO = (K8sInstanceInfoDTO) instanceInfoDTO;
    return K8sInfrastructureDetails.builder()
        .namespace(k8sInstanceInfoDTO.getNamespace())
        .releaseName(k8sInstanceInfoDTO.getReleaseName())
        .build();
  }

  @Override
  public DeploymentReleaseDetails getDeploymentReleaseDetails(
      List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList) {
    List<K8sDeploymentReleaseDetails> k8sDeploymentReleaseDetailsList = new ArrayList<>();

    for (DeploymentInfoDetailsDTO deploymentInfoDetailsDTO : deploymentInfoDetailsDTOList) {
      DeploymentInfoDTO deploymentInfoDTO = deploymentInfoDetailsDTO.getDeploymentInfoDTO();

      if (!(deploymentInfoDTO instanceof K8sDeploymentInfoDTO)) {
        log.warn("Unexpected type of deploymentInfoDto, expected K8sDeploymentInfoDTO found {}",
            deploymentInfoDTO != null ? deploymentInfoDTO.getClass().getSimpleName() : null);
      } else {
        // Todo: Add Cluster Name as well
        K8sDeploymentInfoDTO k8sDeploymentInfoDTO = (K8sDeploymentInfoDTO) deploymentInfoDTO;
        k8sDeploymentReleaseDetailsList.add(K8sDeploymentReleaseDetails.newBuilder()
                                                .setReleaseName(k8sDeploymentInfoDTO.getReleaseName())
                                                .addAllNamespaces(k8sDeploymentInfoDTO.getNamespaces())
                                                .build());
      }
    }
    return DeploymentReleaseDetails.newBuilder()
        .addAllDeploymentDetails(k8sDeploymentReleaseDetailsList.stream().map(Any::pack).collect(toList()))
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (!(serverInstanceInfo instanceof K8sServerInstanceInfo)) {
      throw new InvalidArgumentsException(Pair.of("serverInstanceInfo", "Must be instance of K8sServerInstanceInfo"));
    }

    K8sServerInstanceInfo k8sServerInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfo;

    return K8sInstanceInfoDTO.builder()
        .podName(k8sServerInstanceInfo.getName())
        .namespace(k8sServerInstanceInfo.getNamespace())
        .releaseName(k8sServerInstanceInfo.getReleaseName())
        .podIP(k8sServerInstanceInfo.getPodIP())
        .blueGreenColor(k8sServerInstanceInfo.getBlueGreenColor())
        .containerList(k8sServerInstanceInfo.getContainerList())
        .build();
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!((infrastructureOutcome instanceof K8sDirectInfrastructureOutcome)
            || (infrastructureOutcome instanceof K8sGcpInfrastructureOutcome)
            || (infrastructureOutcome instanceof K8sAwsInfrastructureOutcome)
            || (infrastructureOutcome instanceof K8sAzureInfrastructureOutcome))) {
      throw new InvalidArgumentsException(Pair.of("infrastructureOutcome",
          "Must be instance of K8sDirectInfrastructureOutcome, K8sGcpInfrastructureOutcome, K8sAwsInfrastructureOutcome or K8sAzureInfrastructureOutcome"));
    }
    if (!(serverInstanceInfoList.get(0) instanceof K8sServerInstanceInfo)) {
      throw new InvalidArgumentsException(Pair.of("serverInstanceInfo", "Must be instance of K8sServerInstanceInfo"));
    }

    K8sServerInstanceInfo k8sServerInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfoList.get(0);
    LinkedHashSet<String> namespaces = getNamespaces(serverInstanceInfoList);

    return K8sDeploymentInfoDTO.builder()
        .namespaces(namespaces)
        .releaseName(k8sServerInstanceInfo.getReleaseName())
        .blueGreenStageColor(k8sServerInstanceInfo.getBlueGreenColor())
        .build();
  }

  private LinkedHashSet<String> getNamespaces(@NotNull List<ServerInstanceInfo> serverInstanceInfoList) {
    return serverInstanceInfoList.stream()
        .map(K8sServerInstanceInfo.class ::cast)
        .map(K8sServerInstanceInfo::getNamespace)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
