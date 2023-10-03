/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class StrategyFunctorTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = generateUuid();
  private static final String ORG_ID = generateUuid();
  private static final String PROJECT_ID = generateUuid();
  private static final String APP_ID = generateUuid();
  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String PLAN_ID = generateUuid();
  private static final String PHASE_RUNTIME_ID = generateUuid();
  private static final String PHASE_SETUP_ID = generateUuid();
  private static final String SECTION_RUNTIME_ID = generateUuid();
  private static final String SECTION_SETUP_ID = generateUuid();
  @Mock NodeExecutionsCache nodeExecutionsCache;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMatrixFunctorWithStrategyMetadata() {
    Ambiance ambiance = buildAmbiance(true);
    Map<String, Object> expected = new HashMap<>();
    expected.put("iteration", 0);
    expected.put("iterations", 0);
    expected.put("totalIterations", 0);

    expected.put("identifierPostFix", "_1");
    Map<String, String> matrix = new HashMap<>();
    matrix.put("a", "1");
    expected.put("matrix", matrix);
    expected.put("repeat", new HashMap<>());
    expected.put("currentStatus", "RUNNING");
    for (String key : expected.keySet()) {
      assertThat(new StrategyFunctor(ambiance, nodeExecutionsCache).get(key)).isEqualTo(expected.get(key));
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMatrixFunctorWithoutStrategyMetadata() {
    Ambiance ambiance = buildAmbiance(false);
    Map<String, Object> expected = new HashMap<>();
    assertThat(new StrategyFunctor(ambiance, nodeExecutionsCache).get("matrix")).isNotNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCurrentStatus() {
    NodeExecutionsCache nodeExecutionsCache1 = Mockito.mock(NodeExecutionsCache.class);
    Ambiance ambiance = buildAmbiance(false);
    assertThat(new StrategyFunctor(ambiance, nodeExecutionsCache).get(StrategyFunctor.NODE_KEY)).isNotNull();
    StrategyNodeFunctor strategyNodeFunctor =
        (StrategyNodeFunctor) new StrategyFunctor(ambiance, nodeExecutionsCache).get(StrategyFunctor.NODE_KEY);
    assertThat(strategyNodeFunctor.getNodeExecutionsCache()).isEqualTo(nodeExecutionsCache);

    doReturn(Collections.singletonList(Status.SUCCEEDED))
        .when(nodeExecutionsCache1)
        .findAllTerminalChildrenStatusOnly(any(), eq(true));
    assertThat(new StrategyFunctor(ambiance, nodeExecutionsCache1).get(OrchestrationConstants.CURRENT_STATUS))
        .isEqualTo(Status.SUCCEEDED.name());
  }

  private Ambiance buildAmbiance(boolean addStrategyMetadata) {
    Level phaseLevel =
        Level.newBuilder()
            .setRuntimeId(PHASE_RUNTIME_ID)
            .setSetupId(PHASE_SETUP_ID)
            .setStartTs(1)
            .setIdentifier("i1")
            .setStepType(StepType.newBuilder().setType("PHASE").setStepCategory(StepCategory.STEP).build())
            .build();
    Level sectionLevel =
        Level.newBuilder()
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
            .setGroup("SECTION")
            .setStartTs(2)
            .setIdentifier("i2")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STAGE).build())
            .build();
    Level strategyLevel =
        Level.newBuilder()
            .setRuntimeId("STRATEGY_RUNTIME_ID")
            .setSetupId("STRATEGY_SETUP_ID")
            .setGroup("STRATEGY")
            .setStartTs(2)
            .setIdentifier("i2")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stageLevel =
        Level.newBuilder()
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
            .setGroup("STAGE")
            .setStartTs(3)
            .setIdentifier("i3")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STAGE).build())
            .build();
    if (addStrategyMetadata) {
      stageLevel =
          Level.newBuilder()
              .setRuntimeId(SECTION_RUNTIME_ID)
              .setSetupId(SECTION_SETUP_ID)
              .setGroup("STAGE")
              .setStartTs(3)
              .setIdentifier("i3")
              .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STAGE).build())
              .setStrategyMetadata(
                  StrategyMetadata.newBuilder()
                      .setMatrixMetadata(
                          MatrixMetadata.newBuilder().addMatrixCombination(1).putMatrixValues("a", "1").build())
                      .build())
              .build();
    }
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    levels.add(strategyLevel);
    levels.add(stageLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setPlanId(PLAN_ID)
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .build();
  }
}
