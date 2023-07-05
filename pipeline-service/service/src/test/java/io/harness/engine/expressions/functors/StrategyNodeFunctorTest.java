/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StrategyNodeFunctorTest extends CategoryTest {
  private static final String ACCOUNT_ID = generateUuid();
  private static final String ORG_ID = generateUuid();
  private static final String PROJECT_ID = generateUuid();
  private static final String APP_ID = generateUuid();
  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String PLAN_ID = generateUuid();
  private static final String STAGE_RUNTIME_ID = generateUuid();
  private static final String STAGE_SETUP_ID = generateUuid();

  private static final String STRATEGY_RUNTIME_ID_2 = generateUuid();
  private static final String STRATEGY_SETUP_ID_2 = generateUuid();
  private static final String STRATEGY_RUNTIME_ID_3 = generateUuid();
  private static final String STRATEGY_SETUP_ID_3 = generateUuid();
  private static final String STRATEGY_RUNTIME_ID_4 = generateUuid();
  private static final String STRATEGY_SETUP_ID_4 = generateUuid();

  private static final String SG1_RUNTIME_ID_1 = generateUuid();
  private static final String SG1_SETUP_ID_1 = generateUuid();
  private static final String SG1_RUNTIME_ID_2 = generateUuid();
  private static final String SG1_SETUP_ID_2 = generateUuid();

  private static final String STEP_RUNTIME_ID = generateUuid();
  private static final String STEP_SETUP_ID = generateUuid();

  @Mock NodeExecutionsCache nodeExecutionsCache;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGet() {
    Ambiance ambiance = buildAmbiance();
    StrategyNodeFunctor strategyNodeFunctor =
        StrategyNodeFunctor.builder().nodeExecutionsCache(nodeExecutionsCache).ambiance(ambiance).build();
    doReturn(Collections.singletonList(Status.SUCCEEDED))
        .when(nodeExecutionsCache)
        .findAllTerminalChildrenStatusOnly("STRATEGY_RUNTIME_ID", true);
    assertThat(strategyNodeFunctor.get("strategyNodeId")).isNotNull();
    assertThat(((Map) strategyNodeFunctor.get("strategyNodeId")).get(OrchestrationConstants.CURRENT_STATUS))
        .isEqualTo(Status.SUCCEEDED.name());

    doReturn(Arrays.asList(Status.SUCCEEDED, Status.FAILED))
        .when(nodeExecutionsCache)
        .findAllTerminalChildrenStatusOnly("STRATEGY_RUNTIME_ID", true);
    assertThat(strategyNodeFunctor.get("strategyNodeId")).isNotNull();
    assertThat(((Map) strategyNodeFunctor.get("strategyNodeId")).get(OrchestrationConstants.CURRENT_STATUS))
        .isEqualTo(Status.FAILED.name());

    doReturn(Collections.emptyList())
        .when(nodeExecutionsCache)
        .findAllTerminalChildrenStatusOnly("STRATEGY_RUNTIME_ID", true);
    assertThat(strategyNodeFunctor.get("strategyNodeId1")).isNotNull();
    assertThat(((Map) strategyNodeFunctor.get("strategyNodeId1")).get(OrchestrationConstants.CURRENT_STATUS))
        .isEqualTo("null");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetForMultiStrategyLevels() {
    Ambiance ambiance = buildMultiLevelStrategyAmbiance();
    StrategyNodeFunctor strategyNodeFunctor =
        StrategyNodeFunctor.builder().nodeExecutionsCache(nodeExecutionsCache).ambiance(ambiance).build();
    Map<String, Object> strategyDataMap = (Map<String, Object>) strategyNodeFunctor.get("strategyNodeId_4");

    assertThat(strategyDataMap).isNotNull();
    assertThat(strategyDataMap.get("currentStatus")).isEqualTo("RUNNING");
    assertThat(strategyDataMap.get("identifierPostFix")).isEqualTo("_0");
    assertThat(strategyDataMap.get("iteration")).isEqualTo(0);
    assertThat(strategyDataMap.get("iterations")).isEqualTo(2);
    assertThat(strategyDataMap.get("totalIterations")).isEqualTo(2);
    assertThat(((Map) strategyDataMap.get("matrix")).get("key1")).isEqualTo("val1_step1");
    assertThat(((Map) strategyDataMap.get("matrix")).get("key2")).isEqualTo("val2_step1");
    assertThat(((Map) strategyDataMap.get("matrix")).get("step1_key")).isEqualTo("step1_val");

    strategyDataMap = (Map<String, Object>) strategyNodeFunctor.get("strategyNodeId_3");
    assertThat(strategyDataMap).isNotNull();
    assertThat(strategyDataMap.get("currentStatus")).isEqualTo("RUNNING");
    assertThat(strategyDataMap.get("identifierPostFix")).isEqualTo("_1");
    assertThat(strategyDataMap.get("iteration")).isEqualTo(1);
    assertThat(strategyDataMap.get("iterations")).isEqualTo(4);
    assertThat(strategyDataMap.get("totalIterations")).isEqualTo(4);
    assertThat(((Map) strategyDataMap.get("matrix")).get("key1")).isEqualTo("val1_sg2");
    assertThat(((Map) strategyDataMap.get("matrix")).get("key2")).isEqualTo("val2_sg2");
    assertThat(((Map) strategyDataMap.get("matrix")).get("sg2_key")).isEqualTo("sg2_val");

    strategyDataMap = (Map<String, Object>) strategyNodeFunctor.get("strategyNodeId_2");
    assertThat(strategyDataMap).isNotNull();
    assertThat(strategyDataMap.get("currentStatus")).isEqualTo("RUNNING");
    assertThat(strategyDataMap.get("identifierPostFix")).isEqualTo("_0");
    assertThat(strategyDataMap.get("iteration")).isEqualTo(0);
    assertThat(strategyDataMap.get("iterations")).isEqualTo(3);
    assertThat(strategyDataMap.get("totalIterations")).isEqualTo(3);
    assertThat(((Map) strategyDataMap.get("matrix")).get("key1")).isEqualTo("val1_sg1");
    assertThat(((Map) strategyDataMap.get("matrix")).get("key2")).isEqualTo("val2_sg1");
    assertThat(((Map) strategyDataMap.get("matrix")).get("sg1_key")).isEqualTo("sg1_val");

    strategyDataMap = (Map<String, Object>) strategyNodeFunctor.get("strategyNodeId");
    assertThat(strategyDataMap).isNotNull();
    assertThat(strategyDataMap.get("currentStatus")).isEqualTo("RUNNING");
    assertThat(strategyDataMap.get("identifierPostFix")).isEqualTo("_1");
    assertThat(strategyDataMap.get("iteration")).isEqualTo(1);
    assertThat(strategyDataMap.get("iterations")).isEqualTo(2);
    assertThat(strategyDataMap.get("totalIterations")).isEqualTo(2);
    assertThat(((Map) strategyDataMap.get("matrix")).get("key1")).isEqualTo("val1_stage1");
    assertThat(((Map) strategyDataMap.get("matrix")).get("key2")).isEqualTo("val2_stage1");
    assertThat(((Map) strategyDataMap.get("matrix")).get("stage1_key")).isEqualTo("stage1_val");
  }
  private Ambiance buildAmbiance() {
    Level strategyLevel =
        Level.newBuilder()
            .setRuntimeId("STRATEGY_RUNTIME_ID")
            .setSetupId("STRATEGY_SETUP_ID")
            .setGroup("STRATEGY")
            .setStartTs(2)
            .setIdentifier("strategyNodeId")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stageLevel =
        Level.newBuilder()
            .setRuntimeId(STAGE_RUNTIME_ID)
            .setSetupId(STAGE_SETUP_ID)
            .setGroup("STAGE")
            .setStartTs(3)
            .setIdentifier("i3")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STAGE).build())
            .build();
    List<Level> levels = new ArrayList<>();
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

  private Ambiance buildMultiLevelStrategyAmbiance() {
    Level strategyLevel =
        Level.newBuilder()
            .setRuntimeId("STRATEGY_RUNTIME_ID")
            .setSetupId("STRATEGY_SETUP_ID")
            .setGroup("STRATEGY")
            .setStartTs(2)
            .setIdentifier("strategyNodeId")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stageLevel =
        Level.newBuilder()
            .setRuntimeId(STAGE_RUNTIME_ID)
            .setSetupId(STAGE_SETUP_ID)
            .setGroup("STAGE")
            .setStartTs(3)
            .setIdentifier("i3")
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                            .putMatrixValues("key1", "val1_stage1")
                                                            .putMatrixValues("key2", "val2_stage1")
                                                            .putMatrixValues("stage1_key", "stage1_val")
                                                            .build())
                                     .setTotalIterations(2)
                                     .setCurrentIteration(1)
                                     .build())
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STAGE).build())
            .build();
    Level strategyLevel2 =
        Level.newBuilder()
            .setRuntimeId(STRATEGY_RUNTIME_ID_2)
            .setSetupId(STRATEGY_SETUP_ID_2)
            .setGroup("STRATEGY")
            .setStartTs(4)
            .setIdentifier("strategyNodeId_2")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stepGroupLevel1 =
        Level.newBuilder()
            .setRuntimeId(SG1_RUNTIME_ID_1)
            .setSetupId(SG1_SETUP_ID_1)
            .setGroup("STEP_GROUP")
            .setStartTs(5)
            .setIdentifier("sg1")
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                            .putMatrixValues("key1", "val1_sg1")
                                                            .putMatrixValues("key2", "val2_sg1")
                                                            .putMatrixValues("sg1_key", "sg1_val")
                                                            .build())
                                     .setTotalIterations(3)
                                     .setCurrentIteration(0)
                                     .build())
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    Level strategyLevel3 =
        Level.newBuilder()
            .setRuntimeId(STRATEGY_RUNTIME_ID_3)
            .setSetupId(STRATEGY_SETUP_ID_3)
            .setGroup("STRATEGY")
            .setStartTs(6)
            .setIdentifier("strategyNodeId_3")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stepGroupLevel2 =
        Level.newBuilder()
            .setRuntimeId(SG1_RUNTIME_ID_2)
            .setSetupId(SG1_SETUP_ID_2)
            .setGroup("STEP_GROUP")
            .setStartTs(7)
            .setIdentifier("sg2")
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                            .putMatrixValues("key1", "val1_sg2")
                                                            .putMatrixValues("key2", "val2_sg2")
                                                            .putMatrixValues("sg2_key", "sg2_val")
                                                            .build())
                                     .setTotalIterations(4)
                                     .setCurrentIteration(1)
                                     .build())
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();
    Level strategyLevel4 =
        Level.newBuilder()
            .setRuntimeId(STRATEGY_RUNTIME_ID_4)
            .setSetupId(STRATEGY_SETUP_ID_4)
            .setGroup("STRATEGY")
            .setStartTs(6)
            .setIdentifier("strategyNodeId_4")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stepLevel =
        Level.newBuilder()
            .setRuntimeId(STEP_RUNTIME_ID)
            .setSetupId(STEP_SETUP_ID)
            .setGroup("STEP")
            .setStartTs(7)
            .setIdentifier("step1")
            .setStrategyMetadata(StrategyMetadata.newBuilder()
                                     .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                            .putMatrixValues("key1", "val1_step1")
                                                            .putMatrixValues("key2", "val2_step1")
                                                            .putMatrixValues("step1_key", "step1_val")
                                                            .build())
                                     .setTotalIterations(2)
                                     .setCurrentIteration(0)
                                     .build())
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STEP_GROUP).build())
            .build();

    List<Level> levels = new ArrayList<>();
    levels.add(strategyLevel);
    levels.add(stageLevel);
    levels.add(strategyLevel2);
    levels.add(stepGroupLevel1);
    levels.add(strategyLevel3);
    levels.add(stepGroupLevel2);
    levels.add(strategyLevel4);
    levels.add(stepLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setPlanId(PLAN_ID)
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .build();
  }
}
