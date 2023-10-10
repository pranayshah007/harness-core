/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.rollback.K8sPostProdRollbackInfo;
import io.harness.dtos.rollback.NativeHelmPostProdRollbackInfo;
import io.harness.dtos.rollback.PostProdRollbackSwimLaneInfo;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.mappers.instanceinfo.InstanceInfoMapper;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class PostProdRollbackHelperUtils {
  @Inject private DeploymentSummaryService deploymentSummaryService;
  @Inject private InfrastructureMappingService infrastructureMappingService;

  public PostProdRollbackSwimLaneInfo getSwimlaneInfo(Instance instance) {
    Optional<ArtifactDetails> optionalCurrentArtifactDetail = Optional.ofNullable(instance.getPrimaryArtifact());
    Optional<ArtifactDetails> optionalPreviousArtifactDetails = getPreviousActiveInstanceArtifactDetails(instance);
    switch (instance.getInstanceType()) {
      case K8S_INSTANCE:
        return K8sPostProdRollbackInfo.builder()
            .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
            .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
            .lastDeployedAt(instance.getLastDeployedAt())
            .envName(instance.getEnvName())
            .envIdentifier(instance.getEnvIdentifier())
            .infraName(instance.getInfraName())
            .infraIdentifier(instance.getInfraIdentifier())
            .currentArtifactDisplayName(optionalCurrentArtifactDetail.map(ArtifactDetails::getDisplayName).orElse(null))
            .currentArtifactId(optionalCurrentArtifactDetail.map(ArtifactDetails::getArtifactId).orElse(null))
            .previousArtifactDisplayName(
                optionalPreviousArtifactDetails.map(ArtifactDetails::getDisplayName).orElse(null))
            .previousArtifactId(optionalPreviousArtifactDetails.map(ArtifactDetails::getArtifactId).orElse(null))
            .build();

      case NATIVE_HELM_INSTANCE:
        return NativeHelmPostProdRollbackInfo.builder()
            .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
            .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
            .lastDeployedAt(instance.getLastDeployedAt())
            .envName(instance.getEnvName())
            .envIdentifier(instance.getEnvIdentifier())
            .infraName(instance.getInfraName())
            .infraIdentifier(instance.getInfraIdentifier())
            .currentArtifactDisplayName(optionalCurrentArtifactDetail.map(ArtifactDetails::getDisplayName).orElse(null))
            .currentArtifactId(optionalCurrentArtifactDetail.map(ArtifactDetails::getArtifactId).orElse(null))
            .previousArtifactDisplayName(
                optionalPreviousArtifactDetails.map(ArtifactDetails::getDisplayName).orElse(null))
            .previousArtifactId(optionalPreviousArtifactDetails.map(ArtifactDetails::getArtifactId).orElse(null))
            .build();

      default:
        return null;
    }
  }

  private Optional<ArtifactDetails> getPreviousActiveInstanceArtifactDetails(Instance instance) {
    Optional<InfrastructureMappingDTO> optionalInfrastructureMappingDTO =
        infrastructureMappingService.getByInfrastructureMappingId(instance.getInfrastructureMappingId());
    if (optionalInfrastructureMappingDTO.isEmpty()) {
      return Optional.empty();
    }

    Optional<DeploymentSummaryDTO> deploymentSummaryDTOOptional =
        getDeploymentSummaryDtoOptional(optionalInfrastructureMappingDTO.get(), instance);
    return deploymentSummaryDTOOptional.map(DeploymentSummaryDTO::getArtifactDetails);
  }

  private Optional<DeploymentSummaryDTO> getDeploymentSummaryDtoOptional(
      InfrastructureMappingDTO infrastructureMappingDTO, Instance instance) {
    int N = 2;
    String instanceSyncKey = InstanceInfoMapper.toDTO(instance.getInstanceInfo()).prepareInstanceSyncHandlerKey();
    if (InstanceType.K8S_INSTANCE.equals(instance.getInstanceType())) {
      K8sInstanceInfoDTO k8sInstanceInfoDTO = (K8sInstanceInfoDTO) InstanceInfoMapper.toDTO(instance.getInstanceInfo());
      String blueGreenColor = k8sInstanceInfoDTO.getBlueGreenColor();
      if (isNotEmpty(blueGreenColor)) {
        N = 1;
        k8sInstanceInfoDTO.swapBlueGreenColor();
        instanceSyncKey = k8sInstanceInfoDTO.prepareInstanceSyncHandlerKey();
      }
    }
    return deploymentSummaryService.getNthDeploymentSummaryFromNow(N, instanceSyncKey, infrastructureMappingDTO, false);
  }
}
