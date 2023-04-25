/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.beans.FeatureName.POST_PROD_ROLLBACK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO.PostProdRollbackCheckDTOBuilder;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidRequestException;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.contracts.execution.Status;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.instance.InstanceRepository;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class PostProdRollbackServiceImpl implements PostProdRollbackService {
  // Each instanceType will have its own separate FF.
  private static final Map<InstanceType, FeatureName> INSTANCE_TYPE_TO_FF_MAP =
      Map.of(InstanceType.K8S_INSTANCE, POST_PROD_ROLLBACK);
  @Inject private PipelineServiceClient pipelineServiceClient;
  @Inject private InstanceRepository instanceRepository;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Override
  public PostProdRollbackCheckDTO checkIfRollbackAllowed(String accountIdentifier, String instanceUuid) {
    if (!cdFeatureFlagHelper.isEnabled(accountIdentifier, POST_PROD_ROLLBACK)) {
      throw new InvalidRequestException(String.format(
          "PostProd rollback Feature-flag %s is disabled. Please contact harness support for enabling the feature-flag",
          POST_PROD_ROLLBACK.name()));
    }
    PostProdRollbackCheckDTOBuilder rollbackCheckDTO = PostProdRollbackCheckDTO.builder().isRollbackAllowed(true);
    Instance instance = instanceRepository.getById(instanceUuid);
    if (instance == null) {
      throw new InvalidRequestException(String.format("Could not find the instance for ID %s", instanceUuid));
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
    return rollbackCheckDTO.build();
  }

  @Override
  public PostProdRollbackResponseDTO triggerRollback(String accountIdentifier, String instanceUuid) {
    PostProdRollbackCheckDTO checkDTO = checkIfRollbackAllowed(accountIdentifier, instanceUuid);
    if (!checkDTO.isRollbackAllowed()) {
      return PostProdRollbackResponseDTO.builder()
          .isRollbackTriggered(false)
          .instanceUuid(instanceUuid)
          .message(checkDTO.getMessage())
          .build();
    }
    Instance instance = instanceRepository.getById(instanceUuid);
    Object response = null;
    try {
      // TODO: Get the pipelineIdentifier. That would be used for doing the RBAC check.
      response = NGRestUtils.getResponse(pipelineServiceClient.triggerPostExecutionRollback(
          instance.getLastPipelineExecutionId(), instance.getAccountIdentifier(), instance.getOrgIdentifier(),
          instance.getProjectIdentifier(), "getPipelineId", instance.getStageNodeExecutionId()));
    } catch (Exception ex) {
      throw new InvalidRequestException("Could not trigger the rollback for instance with ID " + instanceUuid, ex);
    }
    String planExecutionId = (String) (((Map<String, Map>) response).get("planExecution")).get("uuid");
    return PostProdRollbackResponseDTO.builder()
        .isRollbackTriggered(true)
        .instanceUuid(instanceUuid)
        .planExecutionId(planExecutionId)
        .build();
  }
}
