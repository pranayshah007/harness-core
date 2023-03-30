/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
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
  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PrincipalInfoHelper principalInfoHelper;

  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String pipeline = "pipelineId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    rollbackModeExecutionHelper =
        new RollbackModeExecutionHelper(nodeExecutionService, pipelineMetadataService, principalInfoHelper);
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
    ExecutionMetadata newMetadata = rollbackModeExecutionHelper.transformExecutionMetadata(
        oldExecutionMetadata, newId, newTriggerInfo, account, org, project, POST_EXECUTION_ROLLBACK);
    assertThat(newMetadata.getExecutionUuid()).isEqualTo(newId);
    assertThat(newMetadata.getTriggerInfo()).isEqualTo(newTriggerInfo);
    assertThat(newMetadata.getRunSequence()).isEqualTo(newRunSeq);
    assertThat(newMetadata.getPrincipalInfo()).isEqualTo(newPrincipalInfo);
    assertThat(newMetadata.getExecutionMode()).isEqualTo(POST_EXECUTION_ROLLBACK);
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
    PlanExecutionMetadata oldPlanExecutionMetadata =
        PlanExecutionMetadata.builder().uuid("randomId").planExecutionId("oldPlanId").processedYaml(original).build();
    String newId = "newId";
    PlanExecutionMetadata newMetadata = rollbackModeExecutionHelper.transformPlanExecutionMetadata(
        oldPlanExecutionMetadata, newId, POST_EXECUTION_ROLLBACK);
    assertThat(newMetadata.getUuid()).isNull();
    assertThat(newMetadata.getPlanExecutionId()).isEqualTo(newId);
    assertThat(newMetadata.getProcessedYaml()).isEqualTo(transformed);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYaml() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    String transformedYaml =
        rollbackModeExecutionHelper.transformProcessedYaml(original, POST_EXECUTION_ROLLBACK, null);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollback() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    doReturn(Collections.singletonList(RetryStageInfo.builder().identifier("s1").name("s1").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionId("ogId");
    String transformedYaml = rollbackModeExecutionHelper.transformProcessedYaml(original, PIPELINE_ROLLBACK, "ogId");
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollbackWithParallelStages() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n"
        + "  - stage:\n"
        + "      identifier: \"s3\"\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s4\"\n"
        + "    - stage:\n"
        + "        identifier: \"s5\"\n";
    doReturn(Arrays.asList(RetryStageInfo.builder().identifier("s1").name("s1").build(),
                 RetryStageInfo.builder().identifier("s2").name("s2").build(),
                 RetryStageInfo.builder().identifier("s3").name("s3").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionId("ogId");
    String transformedYaml = rollbackModeExecutionHelper.transformProcessedYaml(original, PIPELINE_ROLLBACK, "ogId");
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s3\"\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n";
    assertThat(transformedYaml).isEqualTo(expected);
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

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(
        createdPlan, prevExecId, Collections.singletonList("uuid2"), POST_EXECUTION_ROLLBACK);
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