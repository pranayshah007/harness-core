/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.plan.execution.PmsExecutionServiceInfoProvider.PmsNoopModuleInfo;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.execution.beans.StageModuleInfo;
import io.harness.rule.Owner;
import io.harness.steps.approval.stage.ApprovalStageStep;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PmsExecutionInfoProviderTest extends CategoryTest {
  @InjectMocks PmsExecutionServiceInfoProvider pmsExecutionServiceInfoProvider;

  Map<String, String> setupAbstractions =
      Maps.of("accountId", "accountId", "projectIdentifier", "projectIdentfier", "orgIdentifier", "orgIdentifier");
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfo() {
    String levelIdentifier = "levelIdentifier";
    List<Level> levels = Arrays.asList(Level.newBuilder().setIdentifier(levelIdentifier).setRuntimeId("node1").build());

    OrchestrationEvent orchestrationEvent =
        OrchestrationEvent.builder().ambiance(Ambiance.newBuilder().addAllLevels(levels).build()).build();

    PmsPipelineModuleInfo pipelineLevelModuleInfo =
        (PmsPipelineModuleInfo) pmsExecutionServiceInfoProvider.getPipelineLevelModuleInfo(orchestrationEvent);
    assertThat(pipelineLevelModuleInfo).isNotNull();
    assertThat(pipelineLevelModuleInfo.hasApprovalStage).isTrue();
    assertThat(pipelineLevelModuleInfo.getApprovalStageNames().size()).isEqualTo(1);
    assertThat(pipelineLevelModuleInfo.getApprovalStageNames().contains(levelIdentifier)).isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo() {
    OrchestrationEvent orchestrationEvent =
        OrchestrationEvent.builder().ambiance(Ambiance.newBuilder().build()).build();

    StageModuleInfo stageLevelModuleInfo = pmsExecutionServiceInfoProvider.getStageLevelModuleInfo(orchestrationEvent);
    assertThat(stageLevelModuleInfo).isNotNull();
    assertThat(stageLevelModuleInfo).isInstanceOf(PmsNoopModuleInfo.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testShouldRun() {
    String levelIdentifier = "levelIdentifier";
    List<Level> levels = Arrays.asList(Level.newBuilder().setIdentifier(levelIdentifier).setRuntimeId("node1").build());

    OrchestrationEvent orchestrationEvent =
        OrchestrationEvent.builder().ambiance(Ambiance.newBuilder().addAllLevels(levels).build()).build();

    boolean isAmbianceOfTypeApprovalStage = pmsExecutionServiceInfoProvider.shouldRun(orchestrationEvent);
    assertThat(isAmbianceOfTypeApprovalStage).isFalse();

    // event having ambiance with level of type ApprovalStage
    levels = Arrays.asList(Level.newBuilder()
                               .setIdentifier(levelIdentifier)
                               .setRuntimeId("node1")
                               .setStepType(ApprovalStageStep.STEP_TYPE)
                               .build());
    orchestrationEvent =
        OrchestrationEvent.builder().ambiance(Ambiance.newBuilder().addAllLevels(levels).build()).build();
    isAmbianceOfTypeApprovalStage = pmsExecutionServiceInfoProvider.shouldRun(orchestrationEvent);
    assertThat(isAmbianceOfTypeApprovalStage).isTrue();
  }
}
