/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.customDeployment;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.CustomDeploymentNGDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.CustomDeploymentNGInstanceSyncPerpetualTaskParams;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  public static final String OUTPUT_PATH_KEY = "INSTANCE_OUTPUT_PATH";
  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO =
        (CustomDeploymentNGDeploymentInfoDTO) deploymentInfoDTOList.get(0);
    Any perpetualTaskPack = packCustomDeploymentInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO, infrastructureOutcome, customDeploymentNGDeploymentInfoDTO);

    List<ExecutionCapability> executionCapabilities = getExecutionCapability(deploymentInfoDTOList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }
  private Any packCustomDeploymentInstanceSyncPerpetualTaskParams(InfrastructureMappingDTO infrastructureMappingDTO,
      InfrastructureOutcome infrastructureOutcome,
      CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO) {
    return Any.pack(createCustomDeploymentInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO, infrastructureOutcome, customDeploymentNGDeploymentInfoDTO));
  }
  private CustomDeploymentNGInstanceSyncPerpetualTaskParams createCustomDeploymentInstanceSyncPerpetualTaskParams(
      InfrastructureMappingDTO infrastructureMappingDTO, InfrastructureOutcome infraOutcome,
      CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO) {
    return CustomDeploymentNGInstanceSyncPerpetualTaskParams.newBuilder()
        .setScript(customDeploymentNGDeploymentInfoDTO.getInstanceFetchScript())
        .setInfrastructureKey(customDeploymentNGDeploymentInfoDTO.getInfratructureKey())
        .setInstancesListPath(((CustomDeploymentInfrastructureOutcome) infraOutcome).getInstancesListPath())
        .putAllInstanceAttributes(((CustomDeploymentInfrastructureOutcome) infraOutcome).getInstanceAttributes())
        .setAccountId(infrastructureMappingDTO.getAccountIdentifier())
        .setOutputPathKey(OUTPUT_PATH_KEY)
        .build();
  }

  private List<ExecutionCapability> getExecutionCapability(List<DeploymentInfoDTO> deploymentInfoDTOList) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    for (DeploymentInfoDTO deploymentInfoDTO : deploymentInfoDTOList) {
      CustomDeploymentNGDeploymentInfoDTO deploymentPackageInfo =
          (CustomDeploymentNGDeploymentInfoDTO) deploymentInfoDTO;
      SelectorCapability selectorCapability = getSelectorCapability(deploymentPackageInfo);
      if (selectorCapability != null) {
        executionCapabilities.add(selectorCapability);
      }
    }
    return executionCapabilities;
  }

  private SelectorCapability getSelectorCapability(CustomDeploymentNGDeploymentInfoDTO deploymentPackageInfo) {
    if (deploymentPackageInfo.getTags() == null) {
      return null;
    }
    List<String> tagsInDeploymentInfo = deploymentPackageInfo.getTags();
    Set<String> tags = new HashSet<>(tagsInDeploymentInfo);

    return SelectorCapability.builder().selectors(tags).build();
  }
}
