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
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instanceinfo.NativeHelmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.ServerlessAwsLambdaInstanceInfoDTO;
import io.harness.models.InstanceDetailsDTO;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.DX)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceDetailsMapper {
  private final InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;

  public List<InstanceDetailsDTO> toInstanceDetailsDTOList(List<InstanceDTO> instanceDTOList) {
    if (instanceDTOList == null) {
      return new ArrayList<>();
    }
    List<InstanceDetailsDTO> instanceDetailsDTOList = new ArrayList<>();
    instanceDTOList.forEach(instanceDTO -> instanceDetailsDTOList.add(toInstanceDetailsDTO(instanceDTO)));
    return instanceDetailsDTOList;
  }

  private InstanceDetailsDTO toInstanceDetailsDTO(InstanceDTO instanceDTO) {
    AbstractInstanceSyncHandler instanceSyncHandler =
        instanceSyncHandlerFactoryService.getInstanceSyncHandler(getInstanceInfoDTOType(instanceDTO));
    return InstanceDetailsDTO.builder()
        .artifactName(instanceDTO.getPrimaryArtifact().getTag())
        .connectorRef(instanceDTO.getConnectorRef())
        .deployedAt(instanceDTO.getLastDeployedAt())
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
    }
    return null;
  }
}
