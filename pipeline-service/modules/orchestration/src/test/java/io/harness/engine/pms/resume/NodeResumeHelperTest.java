/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.resume.publisher.ResumeMetadata;
import io.harness.execution.NodeExecution;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.util.CloseableIterator;

public class NodeResumeHelperTest extends OrchestrationTestBase {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsOutcomeService pmsOutcomeService;

  @Inject @InjectMocks NodeResumeHelper resumeHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testBuildResponseMap() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String nodeSetupId = generateUuid();
    String planId = generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .addLevels(
                Level.newBuilder()
                    .setRuntimeId(nodeExecutionId)
                    .setSetupId(nodeSetupId)
                    .setNodeType(NodeType.PLAN_NODE.toString())
                    .setStepType(StepType.newBuilder().setType("HTTP").setStepCategory(StepCategory.STEP).build())
                    .build())
            .build();

    ResumeMetadata metadata =
        ResumeMetadata.builder()
            .nodeExecutionUuid(nodeExecutionId)
            .ambiance(ambiance)
            .mode(ExecutionMode.CHILD)
            .latestExecutableResponse(
                ExecutableResponse.newBuilder()
                    .setChild(ChildExecutableResponse.newBuilder().setChildNodeId(generateUuid()).build())
                    .build())
            .module("CD")
            .build();
    String childId = generateUuid();
    String childSetupId = generateUuid();
    NodeExecution child =
        NodeExecution.builder()
            .uuid(childId)
            .ambiance(Ambiance.newBuilder()
                          .setPlanId(planId)
                          .setPlanExecutionId(planExecutionId)
                          .addLevels(Level.newBuilder().setSetupId(childSetupId).setRuntimeId(childId).build())
                          .build())
            .nodeId(childSetupId)
            .build();

    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(Collections.singletonList(child).listIterator());
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             nodeExecutionId, NodeProjectionUtils.fieldsForResponseNotifyData))
        .thenReturn(iterator);
    when(pmsOutcomeService.fetchOutcomeRefs(eq(nodeExecutionId))).thenReturn(Collections.emptyList());
    Map<String, ResponseDataProto> responseMap = resumeHelper.buildResponseMap(metadata, new HashMap<>());
    assertThat(responseMap).containsKey(childId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAccumulationRequiredNonChild() {
    ResumeMetadata metadata = ResumeMetadata.builder().mode(ExecutionMode.ASYNC).build();
    assertThat(resumeHelper.accumulationRequired(metadata)).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAccumulationRequiredChild() {
    ResumeMetadata metadata = ResumeMetadata.builder().mode(ExecutionMode.CHILD).build();
    assertThat(resumeHelper.accumulationRequired(metadata)).isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAccumulationRequiredChildChainSuspend() {
    ResumeMetadata metadata =
        ResumeMetadata.builder()
            .mode(ExecutionMode.CHILD_CHAIN)
            .latestExecutableResponse(
                ExecutableResponse.newBuilder()
                    .setChildChain(ChildChainExecutableResponse.newBuilder().setSuspend(true).build())
                    .build())
            .build();
    assertThat(resumeHelper.accumulationRequired(metadata)).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAccumulationRequiredChildChainNonSuspend() {
    ResumeMetadata metadata =
        ResumeMetadata.builder()
            .mode(ExecutionMode.CHILD_CHAIN)
            .latestExecutableResponse(
                ExecutableResponse.newBuilder()
                    .setChildChain(ChildChainExecutableResponse.newBuilder().setSuspend(false).build())
                    .build())
            .build();
    assertThat(resumeHelper.accumulationRequired(metadata)).isTrue();
  }
}
