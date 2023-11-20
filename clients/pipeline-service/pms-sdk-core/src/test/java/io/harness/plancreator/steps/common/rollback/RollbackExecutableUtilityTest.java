/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.common.rollback;

import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.advisers.rollback.OnFailRollbackOutput;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class RollbackExecutableUtilityTest extends PmsSdkCoreTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPublishRollbackInfoWithStageWrappedInStrategy() {
    ArgumentCaptor<OnFailRollbackOutput> onFailRollbackOutput = ArgumentCaptor.forClass(OnFailRollbackOutput.class);
    when(executionSweepingOutputService.consume(any(), any(), onFailRollbackOutput.capture(), any())).thenReturn(null);
    Ambiance ambiance = buildAmbiance(true);
    RollbackExecutableUtility.publishRollbackInfo(ambiance, null, null, executionSweepingOutputService);
    assertThat(onFailRollbackOutput.getValue().getNextNodeId()).isEqualTo("STRATEGY_SETUP_ID_combinedRollback");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPublishRollbackInfoWithoutStageWrappedInStrategy() {
    ArgumentCaptor<OnFailRollbackOutput> onFailRollbackOutput = ArgumentCaptor.forClass(OnFailRollbackOutput.class);
    when(executionSweepingOutputService.consume(any(), any(), onFailRollbackOutput.capture(), any())).thenReturn(null);
    Ambiance ambiance = buildAmbiance(false);
    RollbackExecutableUtility.publishRollbackInfo(ambiance, null, null, executionSweepingOutputService);
    assertThat(onFailRollbackOutput.getValue().getNextNodeId()).isEqualTo("STAGE_SETUP_ID_combinedRollback");
  }

  private Ambiance buildAmbiance(boolean withStrategy) {
    Level stageLevel = Level.newBuilder()
                           .setRuntimeId("STAGE_RUNTIME_ID")
                           .setSetupId("STAGE_SETUP_ID")
                           .setGroup("STAGE")
                           .setStartTs(3)
                           .setIdentifier("i3")
                           .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(STAGE).build())
                           .build();
    List<Level> levels = new ArrayList<>();
    if (withStrategy) {
      stageLevel = stageLevel.toBuilder().setStrategyMetadata(StrategyMetadata.newBuilder().build()).build();
      Level strategyLevel =
          Level.newBuilder()
              .setRuntimeId("STRATEGY_RUNTIME_ID")
              .setSetupId("STRATEGY_SETUP_ID")
              .setGroup("STRATEGY")
              .setStartTs(2)
              .setIdentifier("i2")
              .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
              .build();
      levels.add(strategyLevel);
    }
    levels.add(stageLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId("PLAN_EXECUTION_ID")
        .setPlanId("PLAN_ID")
        .putAllSetupAbstractions(Map.of(
            "accountId", "ACCOUNT_ID", "orgIdentifier", "ORG_ID", "projectIdentifier", "PROJECT_ID", "appId", "APP_ID"))
        .addAllLevels(levels)
        .build();
  }
}
