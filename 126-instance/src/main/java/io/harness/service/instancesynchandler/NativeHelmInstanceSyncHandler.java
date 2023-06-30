/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome;
import io.harness.delegate.AccountId;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.NativeHelmServerInstanceInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.NativeHelmInstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helper.K8sAndHelmInfrastructureUtility;
import io.harness.helper.K8sCloudConfigMetadata;
import io.harness.helper.KubernetesInfrastructureDTO;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.models.infrastructuredetails.K8sInfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.helm.NativeHelmDeploymentReleaseDetails;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class NativeHelmInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.NATIVE_HELM_INSTANCE_SYNC;
  }

  public String getPerpetualTaskV2Type() {
    return PerpetualTaskType.NATIVE_HELM_INSTANCE_SYNC_V2;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.NATIVE_HELM_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.KUBERNETES_DIRECT;
  } // design issue, not actually used anymore

  // todo: add INSTANCE_SYNC_V2_NG_SUPPORT to TaskType INSTANCE_SYNC_V2_NG_SUPPORT
  public boolean isInstanceSyncV2EnabledAndSupported(String accountId) {
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_K8S_HELM_INSTANCE_SYNC_V2_NG)
        && delegateGrpcClientWrapper.isTaskTypeSupported(AccountId.newBuilder().setId(accountId).build(),
            TaskType.newBuilder().setType("INSTANCE_SYNC_V2_NG_SUPPORT").build());
  }

  @Override
  public InfrastructureOutcome getInfrastructureOutcome(
      String infrastructureKind, DeploymentInfoDTO deploymentInfoDTO, String connectorRef) {
    NativeHelmDeploymentInfoDTO nativeHelmDeploymentInfoDTO = (NativeHelmDeploymentInfoDTO) deploymentInfoDTO;
    KubernetesInfrastructureDTO kubernetesInfrastructureDTO =
        KubernetesInfrastructureDTO.builder()
            .namespaces(nativeHelmDeploymentInfoDTO.getNamespaces())
            .releaseName(nativeHelmDeploymentInfoDTO.getReleaseName())
            .cloudConfigMetadata(nativeHelmDeploymentInfoDTO.getCloudConfigMetadata() == null
                    ? null
                    : nativeHelmDeploymentInfoDTO.getCloudConfigMetadata())
            .build();
    return K8sAndHelmInfrastructureUtility.getInfrastructureOutcome(
        infrastructureKind, kubernetesInfrastructureDTO, connectorRef);
  }

  @Override
  public DeploymentReleaseDetails getDeploymentReleaseDetails(
      InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO) {
    List<NativeHelmDeploymentReleaseDetails> helmDeploymentReleaseDetailsList = new ArrayList<>();
    List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList =
        instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList();
    for (DeploymentInfoDetailsDTO deploymentInfoDetailsDTO : deploymentInfoDetailsDTOList) {
      DeploymentInfoDTO deploymentInfoDTO = deploymentInfoDetailsDTO.getDeploymentInfoDTO();

      if (!(deploymentInfoDTO instanceof NativeHelmDeploymentInfoDTO)) {
        log.warn("Unexpected type of deploymentInfoDto, expected NativeHelmDeploymentInfoDTO found {}",
            deploymentInfoDTO != null ? deploymentInfoDTO.getClass().getSimpleName() : null);
      } else {
        helmDeploymentReleaseDetailsList.add(
            K8sAndHelmInfrastructureUtility.getNativeHelmDeploymentReleaseDetails(deploymentInfoDTO));
      }
    }
    return DeploymentReleaseDetails.builder()
        .taskInfoId(instanceSyncPerpetualTaskInfoDTO.getId())
        .deploymentDetails(new ArrayList<>(helmDeploymentReleaseDetailsList))
        .deploymentType(ServiceSpecType.NATIVE_HELM)
        .build();
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (instanceInfoDTO instanceof NativeHelmInstanceInfoDTO) {
      NativeHelmInstanceInfoDTO nativeHelmInstanceInfoDTO = (NativeHelmInstanceInfoDTO) instanceInfoDTO;
      return K8sInfrastructureDetails.builder()
          .namespace(nativeHelmInstanceInfoDTO.getNamespace())
          .releaseName(nativeHelmInstanceInfoDTO.getReleaseName())
          .build();
    }

    throw new InvalidArgumentsException(Pair.of("instanceInfoDTO", "Must be instance of NativeHelmInstanceInfoDTO"));
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
            || (infrastructureOutcome instanceof K8sAzureInfrastructureOutcome)
            || (infrastructureOutcome instanceof K8sRancherInfrastructureOutcome))) {
      throw new InvalidArgumentsException(Pair.of("infrastructureOutcome",
          "Must be instance of K8sDirectInfrastructureOutcome, K8sGcpInfrastructureOutcome, K8sAwsInfrastructureOutcome, K8sAzureInfrastructureOutcome or K8sRancherInfrastructureOutcome"));
    }

    if (serverInstanceInfoList.get(0) instanceof NativeHelmServerInstanceInfo) {
      NativeHelmServerInstanceInfo nativeHelmServerInstanceInfo =
          (NativeHelmServerInstanceInfo) serverInstanceInfoList.get(0);
      LinkedHashSet<String> namespaces = getNamespaces(serverInstanceInfoList);

      K8sCloudConfigMetadata cloudConfigMetadata =
          K8sAndHelmInfrastructureUtility.getK8sCloudConfigMetadata(infrastructureOutcome);

      return NativeHelmDeploymentInfoDTO.builder()
          .namespaces(namespaces)
          .releaseName(nativeHelmServerInstanceInfo.getReleaseName())
          .helmChartInfo(nativeHelmServerInstanceInfo.getHelmChartInfo())
          .helmVersion(nativeHelmServerInstanceInfo.getHelmVersion())
          .cloudConfigMetadata(cloudConfigMetadata)
          .build();
    }

    throw new InvalidArgumentsException(
        Pair.of("serverInstanceInfo", "Must be instance of NativeHelmServerInstanceInfo"));
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (serverInstanceInfo instanceof NativeHelmServerInstanceInfo) {
      NativeHelmServerInstanceInfo nativeHelmServerInstanceInfo = (NativeHelmServerInstanceInfo) serverInstanceInfo;

      return NativeHelmInstanceInfoDTO.builder()
          .podName(nativeHelmServerInstanceInfo.getPodName())
          .namespace(nativeHelmServerInstanceInfo.getNamespace())
          .releaseName(nativeHelmServerInstanceInfo.getReleaseName())
          .ip(nativeHelmServerInstanceInfo.getIp())
          .helmChartInfo(nativeHelmServerInstanceInfo.getHelmChartInfo())
          .helmVersion(nativeHelmServerInstanceInfo.getHelmVersion())
          .build();
    }

    throw new InvalidArgumentsException(
        Pair.of("serverInstanceInfo", "Must be instance of NativeHelmServerInstanceInfo"));
  }

  private LinkedHashSet<String> getNamespaces(@NotNull List<ServerInstanceInfo> serverInstanceInfoList) {
    return serverInstanceInfoList.stream()
        .map(NativeHelmServerInstanceInfo.class ::cast)
        .map(NativeHelmServerInstanceInfo::getNamespace)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
