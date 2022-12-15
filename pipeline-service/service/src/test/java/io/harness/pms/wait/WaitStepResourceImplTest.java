/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.wait;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.wait.WaitStepAction;
import io.harness.steps.wait.WaitStepService;
import io.harness.wait.WaitStepInstance;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class WaitStepResourceImplTest extends CategoryTest {
  @Mock WaitStepService waitStepService;
  @Mock AccessControlClient accessControlClient;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanExecutionService planExecutionService;
  @InjectMocks WaitStepResourceImpl waitStepResourceImpl;
  String accountId;
  String projectId;
  String orgId;
  String nodeExecutionId;

  @Before
  public void setup() {
    accountId = "accountId";
    orgId = "orgId";
    projectId = "projectId";
    nodeExecutionId = generateUuid();
    doReturn(
        NodeExecution.builder().ambiance(Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build()).build())
        .when(nodeExecutionService)
        .get(nodeExecutionId);
    doReturn(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").build())
        .when(planExecutionService)
        .getExecutionMetadataFromPlanExecution("planExecutionId");
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testMarkAsFailOrSuccess() {
    WaitStepRequestDto waitStepRequestDto =
        WaitStepRequestDto.builder().action(WaitStepActionDto.MARK_AS_SUCCESS).build();
    ResponseDTO<WaitStepResponseDto> responseDTO =
        waitStepResourceImpl.markAsFailOrSuccess(accountId, orgId, projectId, nodeExecutionId, waitStepRequestDto);
    assertTrue(responseDTO.getData().isStatus());
    when(nodeExecutionService.get(nodeExecutionId))
        .thenReturn(NodeExecution.builder()
                        .ambiance(Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build())
                        .build());
    verify(waitStepService, times(1))
        .markAsFailOrSuccess("planExecutionId", nodeExecutionId, WaitStepAction.MARK_AS_SUCCESS);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetWaitStepExecutionDetails() {
    String correlationId = generateUuid();
    Long createdAt = System.currentTimeMillis();
    WaitStepInstance stepInstance = WaitStepInstance.builder()
                                        .waitStepInstanceId(correlationId)
                                        .createdAt(createdAt)
                                        .duration(10000)
                                        .nodeExecutionId(nodeExecutionId)
                                        .build();
    doReturn(stepInstance).when(waitStepService).getWaitStepExecutionDetails(nodeExecutionId);
    ResponseDTO<WaitStepExecutionDetailsDto> response =
        waitStepResourceImpl.getWaitStepExecutionDetails(accountId, orgId, projectId, nodeExecutionId);
    assertEquals(response.getData().getCreatedAt(), createdAt);
    assertEquals(response.getData().getDuration(), 10000);
    assertEquals(response.getData().getNodeExecutionId(), nodeExecutionId);
  }
}
