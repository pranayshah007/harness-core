/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.step;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;

import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock PMSExecutionService pmsExecutionService;
  @InjectMocks PipelineStageStep pipelineStageStep;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAbort() {
    String firstCallBackId = "callBack1";
    String secondCallBackId = "callBack2";
    Ambiance ambiance = Ambiance.newBuilder().build();

    pipelineStageStep.handleAbort(ambiance, PipelineStageStepParameters.builder().build(),
        AsyncExecutableResponse.newBuilder().addCallbackIds(firstCallBackId).addCallbackIds(secondCallBackId).build());
    verify(pmsExecutionService, times(1)).registerInterrupt(PlanExecutionInterruptType.ABORTALL, firstCallBackId, null);
    assertThat(SecurityContextBuilder.getPrincipal()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPipelineStageInfo() {
    String planExecutionId = "planExecutionId";
    String projectId = "projectId";
    String ordId = "orgId";
    Map<String, String> setup = new HashMap<>();
    setup.put("projectIdentifier", projectId);
    setup.put("orgIdentifier", ordId);
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    PipelineStageStepParameters stepParameters =
        PipelineStageStepParameters.builder().stageNodeId("stageNodeId").build();
    PipelineStageInfo info = pipelineStageStep.prepareParentStageInfo(ambiance, stepParameters);
    assertThat(info.getHasParentPipeline()).isEqualTo(true);
    assertThat(info.getStageNodeId()).isEqualTo("stageNodeId");
    assertThat(info.getExecutionId()).isEqualTo(planExecutionId);
    assertThat(info.getProjectId()).isEqualTo(projectId);
    assertThat(info.getOrgId()).isEqualTo(ordId);
    assertThat(info.getRunSequence()).isEqualTo(40);
  }
}
