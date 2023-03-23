/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.AccountId;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.TaskClientParams;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;

@Singleton
@OwnedBy(DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceSyncPerpetualTaskServiceImpl implements InstanceSyncPerpetualTaskService {
  private final DelegateServiceGrpcClient delegateServiceGrpcClient;
  private final InstanceSyncPerpetualTaskServiceRegister perpetualTaskServiceRegister;

  @Override
  public String createPerpetualTask(InfrastructureMappingDTO infrastructureMappingDTO,
      AbstractInstanceSyncHandler abstractInstanceSyncHandler, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = getExecutionBundle(
        infrastructureMappingDTO, abstractInstanceSyncHandler, deploymentInfoDTOList, infrastructureOutcome);

    PerpetualTaskId perpetualTaskId = delegateServiceGrpcClient.createPerpetualTask(
        AccountId.newBuilder().setId(infrastructureMappingDTO.getAccountIdentifier()).build(),
        abstractInstanceSyncHandler.getPerpetualTaskType(), preparePerpetualTaskSchedule(),
        PerpetualTaskClientContextDetails.newBuilder().setExecutionBundle(perpetualTaskExecutionBundle).build(), true,
        getPerpetualTaskDescription(infrastructureMappingDTO));

    return perpetualTaskId.getId();
  }

  @Override
  public void resetPerpetualTask(String accountIdentifier, String perpetualTaskId,
      InfrastructureMappingDTO infrastructureMappingDTO, AbstractInstanceSyncHandler abstractInstanceSyncHandler,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = getExecutionBundle(
        infrastructureMappingDTO, abstractInstanceSyncHandler, deploymentInfoDTOList, infrastructureOutcome);

    delegateServiceGrpcClient.resetPerpetualTask(AccountId.newBuilder().setId(accountIdentifier).build(),
        PerpetualTaskId.newBuilder().setId(perpetualTaskId).build(), perpetualTaskExecutionBundle);
  }

  @Override
  public void deletePerpetualTask(String accountIdentifier, String perpetualTaskId) {
    delegateServiceGrpcClient.deletePerpetualTask(AccountId.newBuilder().setId(accountIdentifier).build(),
        PerpetualTaskId.newBuilder().setId(perpetualTaskId).build());
  }

  // --------------------------- PRIVATE METHODS -------------------------------

  private PerpetualTaskClientContextDetails preparePerpetualTaskClientContext(
      InfrastructureMappingDTO infrastructureMappingDTO) {
    Map<String, String> clientContextMap = new HashMap<>();
    // TODO check if more fields required to be set in perpetual task
    //  ideally infrastructure mapping id should be enough
    clientContextMap.put(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID, infrastructureMappingDTO.getId());
    return PerpetualTaskClientContextDetails.newBuilder()
        .setTaskClientParams(TaskClientParams.newBuilder().putAllParams(clientContextMap).build())
        .build();
  }

  private PerpetualTaskSchedule preparePerpetualTaskSchedule() {
    return PerpetualTaskSchedule.newBuilder()
        .setInterval(Durations.fromMinutes(InstanceSyncConstants.INTERVAL_MINUTES))
        .setTimeout(Durations.fromSeconds(InstanceSyncConstants.TIMEOUT_SECONDS))
        .build();
  }

  private String getPerpetualTaskDescription(InfrastructureMappingDTO infrastructureMappingDTO) {
    return String.format(
        "OrgIdentifier: [%s], ProjectIdentifier: [%s], ServiceIdentifier: [%s], EnvironmentIdentifier: [%s], InfrastructureKey: [%s]",
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
        infrastructureMappingDTO.getServiceIdentifier(), infrastructureMappingDTO.getEnvIdentifier(),
        infrastructureMappingDTO.getInfrastructureKey());
  }

  private PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      AbstractInstanceSyncHandler abstractInstanceSyncHandler, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    InstanceSyncPerpetualTaskHandler instanceSyncPerpetualTaskHandler =
        getInstanceSyncPerpetualTaskHandler(abstractInstanceSyncHandler);
    return instanceSyncPerpetualTaskHandler.getExecutionBundle(
        infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);
  }

  private InstanceSyncPerpetualTaskHandler getInstanceSyncPerpetualTaskHandler(
      AbstractInstanceSyncHandler abstractInstanceSyncHandler) {
    return perpetualTaskServiceRegister.getInstanceSyncPerpetualService(
        abstractInstanceSyncHandler.getPerpetualTaskType());
  }

  public boolean isInstanceSyncV2Enabled() {
    return false;
  }
  public String createPerpetualTaskV2() {
    return null;
  }
}
