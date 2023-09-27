/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.beans.FeatureName.POST_PROD_ROLLBACK;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.dtos.rollback.K8sPostProdRollbackInfo;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO.PostProdRollbackCheckDTOBuilder;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.dtos.rollback.PostProdRollbackSwimLaneInfo;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.entities.RollbackStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.execution.Status;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.instance.InstanceRepository;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
public class PostProdRollbackServiceImpl implements PostProdRollbackService {
  // Each instanceType will have its own separate FF.
  private static final Map<InstanceType, FeatureName> INSTANCE_TYPE_TO_FF_MAP =
      Map.of(InstanceType.K8S_INSTANCE, POST_PROD_ROLLBACK, InstanceType.TAS_INSTANCE, POST_PROD_ROLLBACK,
          InstanceType.ECS_INSTANCE, POST_PROD_ROLLBACK, InstanceType.ASG_INSTANCE, POST_PROD_ROLLBACK,
          InstanceType.SPOT_INSTANCE, POST_PROD_ROLLBACK, InstanceType.NATIVE_HELM_INSTANCE, POST_PROD_ROLLBACK);
  private static final Set<RollbackStatus> ALLOWED_ROLLBACK_START_STATUSES =
      Set.of(RollbackStatus.NOT_STARTED, RollbackStatus.UNAVAILABLE);
  @Inject private PipelineServiceClient pipelineServiceClient;
  @Inject private InstanceRepository instanceRepository;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Override
  public PostProdRollbackCheckDTO checkIfRollbackAllowed(
      String accountIdentifier, String instanceKey, String infraMappingId) {
    if (!cdFeatureFlagHelper.isEnabled(accountIdentifier, POST_PROD_ROLLBACK)) {
      throw new InvalidRequestException(String.format(
          "PostProd rollback Feature-flag %s is disabled. Please contact harness support for enabling the feature-flag",
          POST_PROD_ROLLBACK.name()));
    }
    PostProdRollbackCheckDTOBuilder rollbackCheckDTO = PostProdRollbackCheckDTO.builder().isRollbackAllowed(true);
    Instance instance =
        instanceRepository.getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    if (instance == null) {
      throw new InvalidRequestException(String.format(
          "Could not find the instance for InstanceKey %s and infraMappingId %s", instanceKey, infraMappingId));
    }
    if (instance.getStageStatus() != Status.SUCCEEDED) {
      rollbackCheckDTO.isRollbackAllowed(false);
      rollbackCheckDTO.message(String.format(
          "The deployment stage was not successful in latest execution %s", instance.getLastPipelineExecutionId()));
    } else if (!INSTANCE_TYPE_TO_FF_MAP.containsKey(instance.getInstanceType())
        || !cdFeatureFlagHelper.isEnabled(accountIdentifier, INSTANCE_TYPE_TO_FF_MAP.get(instance.getInstanceType()))) {
      rollbackCheckDTO.isRollbackAllowed(false);
      rollbackCheckDTO.message(
          String.format("The given instanceType %s is not supported for rollback.", instance.getInstanceType().name()));
    }
    if (instance.getRollbackStatus() == null) {
      rollbackCheckDTO.isRollbackAllowed(false);
      rollbackCheckDTO.message("Unable to determine rollback status for given Instance");
    } else if (!ALLOWED_ROLLBACK_START_STATUSES.contains(instance.getRollbackStatus())) {
      rollbackCheckDTO.isRollbackAllowed(false);
      rollbackCheckDTO.message(String.format(
          "Can not start the Rollback. Rollback has already been triggered and the previous rollback status is: %s",
          instance.getRollbackStatus()));
    }
    PostProdRollbackSwimLaneInfo swimLaneInfo = getSwimlaneInfo(instance);
    return rollbackCheckDTO.swimLaneInfo(swimLaneInfo).build();
  }

  @Override
  public PostProdRollbackResponseDTO triggerRollback(
      String accountIdentifier, String instanceKey, String infraMappingId) {
    PostProdRollbackCheckDTO checkDTO = checkIfRollbackAllowed(accountIdentifier, instanceKey, infraMappingId);
    if (!checkDTO.isRollbackAllowed()) {
      return PostProdRollbackResponseDTO.builder()
          .isRollbackTriggered(false)
          .instanceKey(instanceKey)
          .infraMappingId(infraMappingId)
          .message(checkDTO.getMessage())
          .build();
    }
    Instance instance =
        instanceRepository.getInstanceByInstanceKeyAndInfrastructureMappingId(instanceKey, infraMappingId);
    Object response = null;
    try {
      // TODO: Get the pipelineIdentifier. That would be used for doing the RBAC check.
      response = NGRestUtils.getResponse(pipelineServiceClient.triggerPostExecutionRollback(
          instance.getLastPipelineExecutionId(), instance.getAccountIdentifier(), instance.getOrgIdentifier(),
          instance.getProjectIdentifier(), "getPipelineId", instance.getStageNodeExecutionId()));
    } catch (Exception ex) {
      throw new InvalidRequestException(
          String.format("Could not trigger the rollback for instance with InstanceKey %s and infraMappingId %s: %s",
              instanceKey, infraMappingId, ex.getMessage()),
          ex);
    }
    String planExecutionId = (String) (((Map<String, Map>) response).get("planExecution")).get("uuid");
    // since rollback execution is triggered then mark the rollbackStatus as STARTED.
    instance.setRollbackStatus(RollbackStatus.STARTED);
    instanceRepository.save(instance);
    return PostProdRollbackResponseDTO.builder()
        .isRollbackTriggered(true)
        .instanceKey(instanceKey)
        .infraMappingId(infraMappingId)
        .planExecutionId(planExecutionId)
        .build();
  }

  public PostProdRollbackSwimLaneInfo getSwimlaneInfo(Instance instance) {
    switch (instance.getInstanceType()) {
      case K8S_INSTANCE:
        return K8sPostProdRollbackInfo.builder().build();
      default:
        return null;
    }
  }
}
