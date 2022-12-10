/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.wait;

import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_EXECUTE;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_VIEW;
import static io.harness.pms.utils.PmsConstants.PIPELINE;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.steps.wait.WaitStepService;
import io.harness.wait.WaitStepInstance;

import com.google.inject.Inject;

@PipelineServiceAuth
public class WaitStepResourceImpl implements WaitStepResource {
  @Inject WaitStepService waitStepService;
  @Inject private AccessControlClient accessControlClient;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PlanExecutionService planExecutionService;

  @Override
  public ResponseDTO<WaitStepResponseDto> markAsFailOrSuccess(
      String accountId, String orgId, String projectId, String nodeExecutionId, WaitStepRequestDto waitStepRequestDto) {
    String planExecutionId = nodeExecutionService.get(nodeExecutionId).getPlanExecutionId();
    String pipelineIdentifier =
        planExecutionService.getExecutionMetadataFromPlanExecution(planExecutionId).getPipelineIdentifier();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(PIPELINE, pipelineIdentifier), PIPELINE_EXECUTE);
    waitStepService.markAsFailOrSuccess(
        planExecutionId, nodeExecutionId, WaitStepActionMapper.mapWaitStepAction(waitStepRequestDto.getAction()));
    return ResponseDTO.newResponse(WaitStepResponseDto.builder().status(true).build());
  }

  @Override
  public ResponseDTO<WaitStepExecutionDetailsDto> getWaitStepExecutionDetails(
      String accountId, String orgId, String projectId, String nodeExecutionId) {
    String planExecutionId = nodeExecutionService.get(nodeExecutionId).getPlanExecutionId();
    String pipelineIdentifier =
        planExecutionService.getExecutionMetadataFromPlanExecution(planExecutionId).getPipelineIdentifier();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(PIPELINE, pipelineIdentifier), PIPELINE_VIEW);
    WaitStepInstance waitStepInstance = waitStepService.getWaitStepExecutionDetails(nodeExecutionId);
    return ResponseDTO.newResponse(WaitStepExecutionDetailsDto.builder()
                                       .createdAt(waitStepInstance.getCreatedAt())
                                       .duration(waitStepInstance.getDuration())
                                       .nodeExecutionId(waitStepInstance.getNodeExecutionId())
                                       .build());
  }
}
