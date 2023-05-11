/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.AsgInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AwsLambdaInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AwsSamInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AwsSshWinrmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AzureSshWinrmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AzureWebAppInstanceInfoDTO;
import io.harness.dtos.instanceinfo.CustomDeploymentInstanceInfoDTO;
import io.harness.dtos.instanceinfo.EcsInstanceInfoDTO;
import io.harness.dtos.instanceinfo.GitOpsInstanceInfoDTO;
import io.harness.dtos.instanceinfo.GoogleFunctionInstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instanceinfo.NativeHelmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.PdcInstanceInfoDTO;
import io.harness.dtos.instanceinfo.ServerlessAwsLambdaInstanceInfoDTO;
import io.harness.dtos.instanceinfo.SpotInstanceInfoDTO;
import io.harness.dtos.instanceinfo.TasInstanceInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.models.InstanceDetailsDTO;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.DX)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceDetailsMapper {
  private final InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;

  public List<InstanceDetailsDTO> toInstanceDetailsDTOList(List<InstanceDTO> instanceDTOList, Boolean isGitops) {
    if (instanceDTOList == null) {
      return new ArrayList<>();
    }
    List<InstanceDetailsDTO> instanceDetailsDTOList = new ArrayList<>();
    instanceDTOList.forEach(instanceDTO -> instanceDetailsDTOList.add(toInstanceDetailsDTO(instanceDTO, isGitops)));
    return instanceDetailsDTOList;
  }

  private InstanceDetailsDTO toInstanceDetailsDTO(InstanceDTO instanceDTO, Boolean isGitops) {
    AbstractInstanceSyncHandler instanceSyncHandler = instanceSyncHandlerFactoryService.getInstanceSyncHandler(
        getInstanceInfoDTOType(instanceDTO), instanceDTO.getInfrastructureKind());
    ArtifactDetails primaryArtifact = instanceDTO.getPrimaryArtifact();
    String artifactDisplayName = primaryArtifact == null ? null : primaryArtifact.getDisplayName();
    String tag = primaryArtifact == null ? null : primaryArtifact.getTag();
    String artifactName = StringUtils.isNotBlank(artifactDisplayName) ? artifactDisplayName : tag;
    return InstanceDetailsDTO.builder()
        .artifactName(artifactName)
        .connectorRef(instanceDTO.getConnectorRef())
        .deployedAt(isGitops ? instanceDTO.getPodCreatedAt() : instanceDTO.getLastDeployedAt())
        .deployedById(instanceDTO.getLastDeployedById())
        .deployedByName(instanceDTO.getLastDeployedByName())
        .infrastructureDetails(instanceSyncHandler.getInfrastructureDetails(instanceDTO.getInstanceInfoDTO()))
        .pipelineExecutionName(instanceDTO.getLastPipelineExecutionName())
        .podName(instanceDTO.getInstanceInfoDTO().getPodName())
        .instanceInfoDTO(instanceDTO.getInstanceInfoDTO())
        // TODO set terraform instance
        .build();
  }

  private String getInstanceInfoDTOType(InstanceDTO instanceDTO) {
    if (instanceDTO.getInstanceInfoDTO() instanceof K8sInstanceInfoDTO) {
      return ServiceSpecType.KUBERNETES;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof ServerlessAwsLambdaInstanceInfoDTO) {
      return ServiceSpecType.SERVERLESS_AWS_LAMBDA;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof NativeHelmInstanceInfoDTO) {
      return ServiceSpecType.NATIVE_HELM;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof AzureWebAppInstanceInfoDTO) {
      return ServiceSpecType.AZURE_WEBAPP;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof GitOpsInstanceInfoDTO) {
      return ServiceSpecType.GITOPS;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof EcsInstanceInfoDTO) {
      return ServiceSpecType.ECS;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof PdcInstanceInfoDTO) {
      return ((PdcInstanceInfoDTO) instanceDTO.getInstanceInfoDTO()).getServiceType();
    } else if (instanceDTO.getInstanceInfoDTO() instanceof AzureSshWinrmInstanceInfoDTO) {
      return ((AzureSshWinrmInstanceInfoDTO) instanceDTO.getInstanceInfoDTO()).getServiceType();
    } else if (instanceDTO.getInstanceInfoDTO() instanceof AwsSshWinrmInstanceInfoDTO) {
      return ((AwsSshWinrmInstanceInfoDTO) instanceDTO.getInstanceInfoDTO()).getServiceType();
    } else if (instanceDTO.getInstanceInfoDTO() instanceof CustomDeploymentInstanceInfoDTO) {
      return ServiceSpecType.CUSTOM_DEPLOYMENT;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof TasInstanceInfoDTO) {
      return ServiceSpecType.TAS;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof SpotInstanceInfoDTO) {
      return ServiceSpecType.ELASTIGROUP;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof AsgInstanceInfoDTO) {
      return ServiceSpecType.ASG;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof GoogleFunctionInstanceInfoDTO) {
      return ServiceSpecType.GOOGLE_CLOUD_FUNCTIONS;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof AwsSamInstanceInfoDTO) {
      return ServiceSpecType.AWS_SAM;
    } else if (instanceDTO.getInstanceInfoDTO() instanceof AwsLambdaInstanceInfoDTO) {
      return ServiceSpecType.AWS_LAMBDA;
    }
    return null;
  }
}
