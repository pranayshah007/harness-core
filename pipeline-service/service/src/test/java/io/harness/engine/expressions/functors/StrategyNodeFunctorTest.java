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
import io.harness.pms.contracts.execution.Status;
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
  private static final String SECTION_RUNTIME_ID = generateUuid();
  private static final String SECTION_SETUP_ID = generateUuid();
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
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
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
}
