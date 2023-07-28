/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.util.CloseableIterator;

public class RollbackModeExecutionHelperTest extends CategoryTest {
  @Spy RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Mock NodeExecutionService nodeExecutionService;

  @Mock PlanService planService;
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PrincipalInfoHelper principalInfoHelper;
  @Mock RollbackModeYamlTransformer rollbackModeYamlTransformer;

  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String pipeline = "pipelineId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    rollbackModeExecutionHelper = new RollbackModeExecutionHelper(
        nodeExecutionService, planService, pipelineMetadataService, principalInfoHelper, rollbackModeYamlTransformer);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformExecutionMetadata() {
    int newRunSeq = 13134;
    doReturn(newRunSeq).when(pipelineMetadataService).incrementExecutionCounter(account, org, project, pipeline);
    ExecutionPrincipalInfo newPrincipalInfo =
        ExecutionPrincipalInfo.newBuilder().setPrincipalType(PrincipalType.USER).build();
    doReturn(newPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();

    ExecutionMetadata oldExecutionMetadata =
        ExecutionMetadata.newBuilder()
            .setExecutionUuid("oldId")
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).build())
            .setRunSequence(5131)
            .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setPrincipalType(PrincipalType.SERVICE).build())
            .setExecutionMode(ExecutionMode.NORMAL)
            .setPipelineIdentifier(pipeline)
            .build();
    String newId = "newId";
    ExecutionTriggerInfo newTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("ds").build()).build();
    ExecutionMetadata newMetadata = rollbackModeExecutionHelper.transformExecutionMetadata(oldExecutionMetadata, newId,
        newTriggerInfo, account, org, project, POST_EXECUTION_ROLLBACK,
        PipelineStageInfo.newBuilder().setHasParentPipeline(true).build(), null);
    assertThat(newMetadata.getExecutionUuid()).isEqualTo(newId);
    assertThat(newMetadata.getTriggerInfo()).isEqualTo(newTriggerInfo);
    assertThat(newMetadata.getRunSequence()).isEqualTo(newRunSeq);
    assertThat(newMetadata.getPrincipalInfo()).isEqualTo(newPrincipalInfo);
    assertThat(newMetadata.getExecutionMode()).isEqualTo(POST_EXECUTION_ROLLBACK);
    assertThat(newMetadata.getPipelineStageInfo().getHasParentPipeline()).isTrue();
    assertThat(newMetadata.getPostExecutionRollbackInfoCount()).isEqualTo(0);
    assertThat(newMetadata.getOriginalPlanExecutionIdForRollbackMode()).isEqualTo("oldId");
    assertThat(rollbackModeExecutionHelper
                   .transformExecutionMetadata(oldExecutionMetadata, newId, newTriggerInfo, account, org, project,
                       POST_EXECUTION_ROLLBACK, null, null)
                   .getPipelineStageInfo()
                   .getHasParentPipeline())
        .isFalse();

    List<String> stageNodeExecutionIds = Collections.singletonList("stageNodeExecutionId");

    doReturn(Collections.singletonList(
                 NodeExecution.builder()
                     .ambiance(Ambiance.newBuilder()
                                   .addLevels(Level.newBuilder()
                                                  .setSetupId("setupId")
                                                  .setRuntimeId("runtime123")
                                                  .setStrategyMetadata(StrategyMetadata.newBuilder().build())
                                                  .build())
                                   .build())
                     .planNode(PlanNode.builder().uuid("planNodeUuid").build())
                     .build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.fieldsForNodeAndAmbiance);

    newMetadata = rollbackModeExecutionHelper.transformExecutionMetadata(oldExecutionMetadata, newId, newTriggerInfo,
        account, org, project, POST_EXECUTION_ROLLBACK,
        PipelineStageInfo.newBuilder().setHasParentPipeline(true).build(), stageNodeExecutionIds);

    assertThat(newMetadata.getPostExecutionRollbackInfoCount()).isEqualTo(1);
    assertThat(newMetadata.getPostExecutionRollbackInfo(0).getPostExecutionRollbackStageId()).isEqualTo("setupId");
    assertThat(newMetadata.getPostExecutionRollbackInfo(0).getRollbackStageStrategyMetadata())
        .isEqualTo(StrategyMetadata.newBuilder().build());
    assertThat(newMetadata.getPostExecutionRollbackInfo(0).getOriginalStageExecutionId()).isEqualTo("runtime123");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformPlanExecutionMetadata() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    String transformed = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n";
    doReturn(transformed)
        .when(rollbackModeYamlTransformer)
        .transformProcessedYaml(original, POST_EXECUTION_ROLLBACK, "oldPlanId");
    PlanExecutionMetadata oldPlanExecutionMetadata =
        PlanExecutionMetadata.builder().uuid("randomId").planExecutionId("oldPlanId").processedYaml(original).build();
    String newId = "newId";
    PlanExecutionMetadata newMetadata = rollbackModeExecutionHelper.transformPlanExecutionMetadata(
        oldPlanExecutionMetadata, newId, POST_EXECUTION_ROLLBACK, null, null);
    assertThat(newMetadata.getUuid()).isNull();
    assertThat(newMetadata.getPlanExecutionId()).isEqualTo(newId);
    assertThat(newMetadata.getStagesExecutionMetadata()).isNull();
    assertThat(newMetadata.getProcessedYaml()).isEqualTo(transformed);

    List<String> stageNodeExecutionIds = Collections.singletonList("stageNodeExecutionId");
    String stageFqn = "pipeline.stages.stage1";
    doReturn(Collections.singletonList(
                 NodeExecution.builder().planNode(PlanNode.builder().stageFqn(stageFqn).build()).build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(new HashSet<>(stageNodeExecutionIds), Set.of(NodeExecutionKeys.planNode));
    newMetadata = rollbackModeExecutionHelper.transformPlanExecutionMetadata(
        oldPlanExecutionMetadata, newId, POST_EXECUTION_ROLLBACK, stageNodeExecutionIds, null);
    assertThat(newMetadata.getStagesExecutionMetadata().getStageIdentifiers().size()).isEqualTo(1);
    assertThat(newMetadata.getStagesExecutionMetadata().getStageIdentifiers().get(0)).isEqualTo(stageFqn);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformPlanForRollbackMode() {
    StepType stepType = StepType.newBuilder().setStepCategory(StepCategory.STEP).build();
    String prevExecId = "prevExecId";

    PlanNode stageNode = PlanNode.builder()
                             .uuid("s1")
                             .stageFqn("pipeline.stages.s1")
                             .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                             .skipGraphType(SkipType.NOOP)
                             .build();

    PlanNode toBeReplaced = PlanNode.builder()
                                .uuid("uuid1")
                                .stageFqn("pipeline.stages.s1")
                                .stepType(stepType)
                                .advisorObtainmentsForExecutionMode(Collections.singletonMap(POST_EXECUTION_ROLLBACK,
                                    Collections.singletonList(AdviserObtainment.newBuilder().build())))
                                .skipGraphType(SkipType.NOOP)
                                .build();
    NodeExecution nodeExecutionForUuid1 =
        NodeExecution.builder().planNode(toBeReplaced).stepType(stepType).uuid("nodeExecForUuid1").build();

    List<NodeExecution> nodeExecutionList = Collections.singletonList(nodeExecutionForUuid1);

    PlanNode tobePreserved = PlanNode.builder().uuid("uuid2").stepType(stepType).skipGraphType(SkipType.NOOP).build();

    Plan createdPlan =
        Plan.builder().planNode(toBeReplaced).planNode(tobePreserved).planNode(stageNode).valid(true).build();

    CloseableIterator<NodeExecution> iterator = createCloseableIterator(nodeExecutionList.iterator());
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecId, Collections.singletonList("pipeline.stages.s1"),
            NodeProjectionUtils.fieldsForIdentityNodeCreation);

    when(planService.fetchNode(toBeReplaced.getUuid())).thenReturn(toBeReplaced);
    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecId,
        Collections.singletonList("uuid2"), POST_EXECUTION_ROLLBACK, Collections.singletonList("pipeline.stages.s1"));
    List<Node> nodes = transformedPlan.getPlanNodes();
    assertThat(nodes).hasSize(3);
    assertThat(nodes).contains(
        stageNode.withPreserveInRollbackMode(true), tobePreserved.withPreserveInRollbackMode(true));
    List<Node> identityNodes =
        nodes.stream().filter(node -> node.getNodeType() == NodeType.IDENTITY_PLAN_NODE).collect(Collectors.toList());
    assertThat(identityNodes).hasSize(1);
    IdentityPlanNode identityNode = (IdentityPlanNode) identityNodes.get(0);
    assertThat(identityNode.getUuid()).isEqualTo("uuid1");
    assertThat(identityNode.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);
    assertThat(identityNode.getAdviserObtainments()).hasSize(1);
    assertThat(identityNode.getUseAdviserObtainments()).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddAdvisorsToIdentityNodes() {
    Map<String, Node> planNodeIDToUpdatedPlanNodes = new HashMap<>();
    planNodeIDToUpdatedPlanNodes.put("uuid1", IdentityPlanNode.builder().build());
    StepType stepType = StepType.newBuilder().setStepCategory(StepCategory.STEP).build();
    PlanNode tobePreserved = PlanNode.builder().uuid("uuid2").stepType(stepType).skipGraphType(SkipType.NOOP).build();
    PlanNode toBeReplaced =
        PlanNode.builder()
            .uuid("uuid1")
            .stageFqn("pipeline.stages.s1")
            .stepType(stepType)
            .advisorObtainmentsForExecutionMode(
                Map.of(POST_EXECUTION_ROLLBACK, Collections.singletonList(AdviserObtainment.newBuilder().build()),
                    PIPELINE_ROLLBACK, Collections.singletonList(AdviserObtainment.newBuilder().build())))
            .skipGraphType(SkipType.NOOP)
            .build();
    PlanNode stageNode = PlanNode.builder()
                             .uuid("s1")
                             .stageFqn("pipeline.stages.s1")
                             .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                             .skipGraphType(SkipType.NOOP)
                             .build();
    Plan createdPlan =
        Plan.builder().planNode(toBeReplaced).planNode(tobePreserved).planNode(stageNode).valid(true).build();

    rollbackModeExecutionHelper.addAdvisorsToIdentityNodes(createdPlan, planNodeIDToUpdatedPlanNodes,
        POST_EXECUTION_ROLLBACK, Collections.singletonList("pipeline.stages.s1"));

    IdentityPlanNode updatedNode = (IdentityPlanNode) planNodeIDToUpdatedPlanNodes.get("uuid1");
    assertThat(updatedNode.getUseAdviserObtainments()).isTrue();
    assertThat(updatedNode.getAdviserObtainments()).hasSize(1);

    planNodeIDToUpdatedPlanNodes.put("uuid1", IdentityPlanNode.builder().build());
    rollbackModeExecutionHelper.addAdvisorsToIdentityNodes(
        createdPlan, planNodeIDToUpdatedPlanNodes, POST_EXECUTION_ROLLBACK, null);
    updatedNode = (IdentityPlanNode) planNodeIDToUpdatedPlanNodes.get("uuid1");
    assertThat(updatedNode.getUseAdviserObtainments()).isFalse();
    assertThat(updatedNode.getAdviserObtainments()).isNull();

    planNodeIDToUpdatedPlanNodes.put("uuid1", IdentityPlanNode.builder().build());
    rollbackModeExecutionHelper.addAdvisorsToIdentityNodes(
        createdPlan, planNodeIDToUpdatedPlanNodes, PIPELINE_ROLLBACK, Collections.emptyList());
    updatedNode = (IdentityPlanNode) planNodeIDToUpdatedPlanNodes.get("uuid1");
    assertThat(updatedNode.getUseAdviserObtainments()).isTrue();
    assertThat(updatedNode.getAdviserObtainments()).hasSize(1);
  }

  public static <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}