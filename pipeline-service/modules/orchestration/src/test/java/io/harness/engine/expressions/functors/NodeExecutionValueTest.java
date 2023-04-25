/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRASHANT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.steps.TestStepParameters;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.logging.impl.NoOpLog;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionValueTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsOutcomeService pmsOutcomeService;
  @Mock PmsSweepingOutputService pmsSweepingOutputService;

  @Mock PlanService planService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private JexlEngine engine;
  NodeExecution nodeExecution1;
  NodeExecution nodeExecution2;
  NodeExecution nodeExecution3;
  NodeExecution nodeExecution4;
  NodeExecution nodeExecution5;
  NodeExecution nodeExecution6;
  NodeExecution nodeExecution7;
  NodeExecution nodeExecution8;

  String planId = generateUuid();
  String planExecutionId = generateUuid();

  @Before
  public void setup() {
    String nodeExecution1Id = generateUuid();
    String nodeExecution2Id = generateUuid();
    String nodeExecution3Id = generateUuid();
    String nodeExecution4Id = generateUuid();
    String nodeExecution5Id = generateUuid();
    String nodeExecution6Id = generateUuid();
    String nodeExecution7Id = generateUuid();
    String nodeExecution8Id = generateUuid();
    engine = new JexlBuilder().logger(new NoOpLog()).create();

    PlanNode node1 = preparePlanNode(false, "a");
    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder()
                                           .setPlanExecutionId(planExecutionId)
                                           .setPlanId(planId)
                                           .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, node1));
    when(planService.fetchNode(planId, node1.getUuid())).thenReturn(node1);
    nodeExecution1 = NodeExecution.builder()
                         .uuid(nodeExecution1Id)
                         .ambiance(ambianceBuilder.build())
                         .resolvedStepParameters(prepareStepParameters("ao"))
                         .nodeId(node1.getUuid())
                         .name(node1.getName())
                         .stepType(node1.getStepType())
                         .identifier(node1.getIdentifier())
                         .module(node1.getServiceName())
                         .skipGraphType(node1.getSkipGraphType())
                         .build();

    PlanNode node2 = preparePlanNode(false, "b");
    when(planService.fetchNode(planId, node2.getUuid())).thenReturn(node2);
    nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecution2Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, node2)).build())
            .nodeId(node2.getUuid())
            .name(node2.getName())
            .stepType(node2.getStepType())
            .identifier(node2.getIdentifier())
            .module(node2.getServiceName())
            .skipGraphType(node2.getSkipGraphType())
            .resolvedStepParameters(prepareStepParameters("bo"))
            .parentId(nodeExecution1Id)
            .nextId(nodeExecution1Id)
            .build();

    PlanNode node3 = preparePlanNode(true, "c");
    when(planService.fetchNode(planId, node3.getUuid())).thenReturn(node3);
    nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecution3Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, node3)).build())
            .nodeId(node3.getUuid())
            .name(node3.getName())
            .stepType(node3.getStepType())
            .identifier(node3.getIdentifier())
            .module(node3.getServiceName())
            .skipGraphType(node3.getSkipGraphType())
            .resolvedStepParameters(prepareStepParameters("co"))
            .parentId(nodeExecution1Id)
            .previousId(nodeExecution2Id)
            .build();

    PlanNode node4 = preparePlanNode(false, "d", "di1", "STAGE");
    when(planService.fetchNode(planId, node4.getUuid())).thenReturn(node4);
    nodeExecution4 =
        NodeExecution.builder()
            .uuid(nodeExecution4Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, node3))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution4Id, node4,
                              StrategyMetadata.newBuilder()
                                  .setMatrixMetadata(MatrixMetadata.newBuilder().putMatrixValues("os", "test").build())
                                  .setCurrentIteration(2)
                                  .build(),
                              false))
                          .build())
            .nodeId(node4.getUuid())
            .name(node4.getName())
            .stepType(node4.getStepType())
            .identifier(node4.getIdentifier())
            .module(node4.getServiceName())
            .skipGraphType(node4.getSkipGraphType())
            .parentId(nodeExecution3Id)
            .nextId(nodeExecution5Id)
            .build();

    PlanNode node5 = preparePlanNode(false, "d", "di2");
    when(planService.fetchNode(planId, node5.getUuid())).thenReturn(node5);
    nodeExecution5 = NodeExecution.builder()
                         .uuid(nodeExecution5Id)
                         .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, node3))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5Id, node5))
                                       .build())
                         .nodeId(node5.getUuid())
                         .name(node5.getName())
                         .stepType(node5.getStepType())
                         .identifier(node5.getIdentifier())
                         .module(node5.getServiceName())
                         .skipGraphType(node5.getSkipGraphType())
                         .resolvedStepParameters(prepareStepParameters("do2"))
                         .parentId(nodeExecution3Id)
                         .previousId(nodeExecution4Id)
                         .build();

    PlanNode node6 = preparePlanNode(false, "e");
    when(planService.fetchNode(planId, node6.getUuid())).thenReturn(node6);
    nodeExecution6 = NodeExecution.builder()
                         .uuid(nodeExecution6Id)
                         .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, node3))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution4Id, node4))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution6Id, node6))
                                       .build())
                         .nodeId(node6.getUuid())
                         .name(node6.getName())
                         .stepType(node6.getStepType())
                         .identifier(node6.getIdentifier())
                         .module(node6.getServiceName())
                         .skipGraphType(node6.getSkipGraphType())
                         .resolvedStepParameters(prepareStepParameters("eo"))
                         .parentId(nodeExecution4Id)
                         .build();

    PlanNode node7 = preparePlanNode(false, "f");
    when(planService.fetchNode(planId, node7.getUuid())).thenReturn(node7);
    nodeExecution7 = NodeExecution.builder()
                         .uuid(nodeExecution7Id)
                         .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, node3))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution4Id, node4))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution6Id, node6))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution7Id, node7))
                                       .build())
                         .nodeId(node7.getUuid())
                         .name(node7.getName())
                         .stepType(node7.getStepType())
                         .identifier(node7.getIdentifier())
                         .module(node7.getServiceName())
                         .skipGraphType(node7.getSkipGraphType())
                         .resolvedStepParameters(prepareStepParameters("eo"))
                         .parentId(nodeExecution6Id)
                         .nextId(nodeExecution8Id)
                         .build();

    PlanNode node8 = preparePlanNode(false, "g");
    when(planService.fetchNode(planId, node8.getUuid())).thenReturn(node8);
    nodeExecution8 = NodeExecution.builder()
                         .uuid(nodeExecution8Id)
                         .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, node3))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution4Id, node4))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution6Id, node6))
                                       .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution8Id, node8))
                                       .build())
                         .nodeId(node8.getUuid())
                         .name(node8.getName())
                         .stepType(node8.getStepType())
                         .identifier(node8.getIdentifier())
                         .module(node8.getServiceName())
                         .skipGraphType(node8.getSkipGraphType())
                         .resolvedStepParameters(prepareStepParameters("eo"))
                         .parentId(nodeExecution6Id)
                         .previousId(nodeExecution7Id)
                         .build();

    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution1.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution1);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution2.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution2);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution3.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution3);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution4.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution4);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution5.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution5);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution6.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution6);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution7.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution7);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution8.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution8);

    List<NodeExecution> nodeExecutionsList1 = Collections.singletonList(nodeExecution1);
    CloseableIterator<NodeExecution> iterator1 =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionsList1.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, null, NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator1);

    List<NodeExecution> nodeExecutionList2 = asList(nodeExecution2, nodeExecution3);
    CloseableIterator<NodeExecution> iterator2 =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList2.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution1.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator2);

    List<NodeExecution> nodeExecutionList3 = asList(nodeExecution4, nodeExecution5);
    CloseableIterator<NodeExecution> iterator3 =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList3.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution3.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator3);

    List<NodeExecution> nodeExecutionList4 = Collections.singletonList(nodeExecution6);
    CloseableIterator<NodeExecution> iterator4 =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList4.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution4.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator4);

    List<NodeExecution> nodeExecutionList5 = asList(nodeExecution7, nodeExecution8);
    CloseableIterator<NodeExecution> iterator5 =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList5.iterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution6.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator5);

    CloseableIterator<NodeExecution> emptyIterator =
        OrchestrationTestHelper.createCloseableIterator(Collections.emptyListIterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution2.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution5.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution7.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution8.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeExecutionChildFunctor() {
    Ambiance newAmbiance = nodeExecution1.getAmbiance();
    NodeExecutionChildFunctor functor =
        NodeExecutionChildFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, planService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(newAmbiance)
            .build();
    NodeExecutionMap nodeExecutionMap = (NodeExecutionMap) functor.bind();
    assertThat(engine.getProperty(nodeExecutionMap, "param")).isEqualTo("ao");
    assertThat(engine.getProperty(nodeExecutionMap, "b.param")).isEqualTo("bo");
    assertThat(engine.getProperty(nodeExecutionMap, "d[0].param")).isEqualTo("di1");
    assertThat(engine.getProperty(nodeExecutionMap, "d[1].param")).isEqualTo("do2");
    assertThat(engine.getProperty(nodeExecutionMap, "d[0].e.param")).isEqualTo("eo");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeExecutionAncestorFunctor() {
    Ambiance newAmbiance = nodeExecution6.getAmbiance();
    NodeExecutionAncestorFunctor functor =
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, planService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(newAmbiance)
            .groupAliases(ImmutableMap.of("stage", "STAGE"))
            .build();
    assertThat(engine.getProperty(functor, "stage.param")).isEqualTo("di1");
    assertThat(engine.getProperty(functor, "stage.e.param")).isEqualTo("eo");
    assertThat(engine.getProperty(functor, "a.b.param")).isEqualTo("bo");
    assertThat(engine.getProperty(functor, "a.d[0].param")).isEqualTo("di1");
    assertThat(engine.getProperty(functor, "a.d[1].param")).isEqualTo("do2");
    assertThat(engine.getProperty(functor, "a.d[0].e.param")).isEqualTo("eo");
    assertThat(engine.getProperty(functor, "d.param")).isEqualTo("di1");
    assertThat(engine.getProperty(functor, "d.e.param")).isEqualTo("eo");
    assertThat(engine.getProperty(functor, "e.param")).isEqualTo("eo");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeExecutionAncestorFunctorMatrix() {
    Ambiance newAmbiance = nodeExecution6.getAmbiance();
    NodeExecutionAncestorFunctor functor =
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, planService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .pmsSweepingOutputService(pmsSweepingOutputService)
            .ambiance(newAmbiance)
            .groupAliases(ImmutableMap.of("stage", "STAGE"))
            .build();
    assertThat(engine.getProperty(functor, "stage.matrix.os")).isEqualTo("test");
    assertThat(engine.getProperty(functor, "stage.iteration")).isEqualTo(2);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNodeExecutionCurrentStatusSucceeded() {
    Ambiance newAmbiance = nodeExecution8.getAmbiance();
    NodeExecutionAncestorFunctor functor =
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, planService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(newAmbiance)
            .groupAliases(ImmutableMap.of("stage", "STAGE"))
            .build();

    Reflect.on(nodeExecution4).set(NodeExecutionKeys.status, Status.RUNNING);
    Reflect.on(nodeExecution6).set(NodeExecutionKeys.status, Status.RUNNING);
    Reflect.on(nodeExecution7).set(NodeExecutionKeys.status, Status.SUCCEEDED);
    Reflect.on(nodeExecution8).set(NodeExecutionKeys.status, Status.QUEUED);

    when(nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetries(planExecutionId, nodeExecution4.getUuid(),
             StatusUtils.finalStatuses(), false, Sets.newHashSet(NodeExecutionKeys.parentId, NodeExecutionKeys.status),
             false))
        .thenReturn(Collections.singletonList(nodeExecution7));
    // Check current status for SUCCEEDED
    assertThat(engine.getProperty(functor, "stage.currentStatus")).isEqualTo("SUCCEEDED");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNodeExecutionCurrentStatusFailed() {
    Ambiance newAmbiance = nodeExecution8.getAmbiance();
    NodeExecutionAncestorFunctor functor =
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, planService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(newAmbiance)
            .groupAliases(ImmutableMap.of("stage", "STAGE"))
            .build();

    Reflect.on(nodeExecution4).set(NodeExecutionKeys.status, Status.RUNNING);
    Reflect.on(nodeExecution6).set(NodeExecutionKeys.status, Status.RUNNING);
    Reflect.on(nodeExecution7).set(NodeExecutionKeys.status, Status.FAILED);
    Reflect.on(nodeExecution8).set(NodeExecutionKeys.status, Status.QUEUED);

    when(nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetries(
             planExecutionId, nodeExecution4.getUuid(), null, false, Collections.emptySet(), false))
        .thenReturn(Collections.singletonList(nodeExecution7));
    // Check current status for FAILED
    assertThat(engine.getProperty(functor, "stage.currentStatus")).isEqualTo("FAILED");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNodeExecutionCurrentStatusErrored() {
    Ambiance newAmbiance = nodeExecution8.getAmbiance();
    NodeExecutionAncestorFunctor functor =
        NodeExecutionAncestorFunctor.builder()
            .nodeExecutionsCache(new NodeExecutionsCache(nodeExecutionService, planService, newAmbiance))
            .pmsOutcomeService(pmsOutcomeService)
            .ambiance(newAmbiance)
            .groupAliases(ImmutableMap.of("stage", "STAGE"))
            .build();

    Reflect.on(nodeExecution4).set(NodeExecutionKeys.status, Status.RUNNING);
    Reflect.on(nodeExecution6).set(NodeExecutionKeys.status, Status.RUNNING);
    Reflect.on(nodeExecution7).set(NodeExecutionKeys.status, Status.ERRORED);
    Reflect.on(nodeExecution8).set(NodeExecutionKeys.status, Status.QUEUED);

    when(nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetries(
             planExecutionId, nodeExecution4.getUuid(), null, false, Collections.emptySet(), false))
        .thenReturn(Collections.singletonList(nodeExecution7));
    // Check current status for ERRORED
    assertThat(engine.getProperty(functor, "stage.currentStatus")).isEqualTo("ERRORED");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNodeExecutionQualifiedFunctor() {
    NodeExecutionQualifiedFunctor functor = NodeExecutionQualifiedFunctor.builder()
                                                .nodeExecutionsCache(new NodeExecutionsCache(
                                                    nodeExecutionService, planService, nodeExecution1.getAmbiance()))
                                                .pmsOutcomeService(pmsOutcomeService)
                                                .ambiance(nodeExecution1.getAmbiance())
                                                .build();
    NodeExecutionMap nodeExecutionMap = (NodeExecutionMap) functor.bind();
    assertThat(engine.getProperty(nodeExecutionMap, "a.b.param")).isEqualTo("bo");
    assertThat(engine.getProperty(nodeExecutionMap, "a.d[0].param")).isEqualTo("di1");
    assertThat(engine.getProperty(nodeExecutionMap, "a.d[1].param")).isEqualTo("do2");
    assertThat(engine.getProperty(nodeExecutionMap, "a.d[0].e.param")).isEqualTo("eo");
  }

  private PlanNode preparePlanNode(boolean skipExpressionChain, String identifier) {
    return preparePlanNode(skipExpressionChain, identifier, identifier + "i");
  }

  private PlanNode preparePlanNode(boolean skipExpressionChain, String identifier, String paramValue) {
    return preparePlanNode(skipExpressionChain, identifier, paramValue, null);
  }

  private PlanNode preparePlanNode(
      boolean skipExpressionChain, String identifier, String paramValue, String groupName) {
    return PlanNode.builder()
        .uuid(generateUuid())
        .group(groupName)
        .name(identifier + "n")
        .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
        .identifier(identifier)
        .skipExpressionChain(skipExpressionChain)
        .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(prepareStepParameters(paramValue))))
        .build();
  }

  private Map<String, Object> prepareStepParameters(String paramValue) {
    return RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(paramValue).build());
  }
}
