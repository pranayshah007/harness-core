/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.IdentityPlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityNodeExecutionStrategyHelperTest {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;
  @InjectMocks IdentityNodeExecutionStrategyHelper identityNodeExecutionStrategyHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInterruptHistory() {
    List<String> retryIds = List.of("retryId1", "retryId2");
    Map<String, String> retryIdsMap =
        Map.of(retryIds.get(0), "mappedId1", retryIds.get(1), "mappedId2", "retryId3", "mappedId3");

    List<InterruptEffect> interruptHistory = new ArrayList<>();
    interruptHistory.add(
        InterruptEffect.builder()
            .interruptType(InterruptType.RETRY)
            .interruptId("interruptId1")
            .tookEffectAt(100L)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().setRetryId(retryIds.get(0)).build())
                    .build())
            .build());

    interruptHistory.add(
        InterruptEffect.builder()
            .interruptType(InterruptType.RETRY)
            .interruptId("interruptId2")
            .tookEffectAt(150L)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().setRetryId(retryIds.get(1)).build())
                    .build())
            .build());

    interruptHistory.add(InterruptEffect.builder()
                             .interruptType(InterruptType.MARK_SUCCESS)
                             .interruptId("interruptId3")
                             .tookEffectAt(200L)
                             .interruptConfig(InterruptConfig.newBuilder().build())
                             .build());

    List<InterruptEffect> updatedInterruptHistory =
        identityNodeExecutionStrategyHelper.getUpdatedInterruptHistory(interruptHistory, retryIdsMap);
    assertEquals(updatedInterruptHistory.size(), interruptHistory.size());
    for (InterruptEffect interruptEffect : updatedInterruptHistory) {
      Optional<InterruptEffect> optional = interruptHistory.stream()
                                               .filter(o -> o.getInterruptId().equals(interruptEffect.getInterruptId()))
                                               .findFirst();
      assertThat(optional.isPresent()).isTrue();
      InterruptEffect originalInterruptEffect = optional.get();
      assertEquals(originalInterruptEffect.getInterruptType(), interruptEffect.getInterruptType());
      assertEquals(originalInterruptEffect.getTookEffectAt(), interruptEffect.getTookEffectAt());
      if (interruptEffect.getInterruptConfig().hasRetryInterruptConfig()) {
        assertThat(originalInterruptEffect.getInterruptConfig().hasRetryInterruptConfig()).isTrue();
        assertEquals(interruptEffect.getInterruptConfig().getRetryInterruptConfig().getRetryId(),
            retryIdsMap.get(originalInterruptEffect.getInterruptConfig().getRetryInterruptConfig().getRetryId()));
      } else {
        assertEquals(interruptEffect.getInterruptConfig(), originalInterruptEffect.getInterruptConfig());
      }
    }
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCopyNodeExecutionsForRetriedNodes() {
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .ambiance(Ambiance.newBuilder()
                                                    .addLevels(Level.newBuilder().setRuntimeId("runtimeId1").build())
                                                    .addLevels(Level.newBuilder().setRuntimeId("runtimeId2").build())
                                                    .build())
                                      .build();
    String retryId1 = "retryId1";
    String retryId2 = "retryId2";
    List<String> retryIdsList = List.of(retryId1, retryId2);

    List<NodeExecution> retriedNodeExecutions = new ArrayList<>();
    IdentityPlanNode node =
        IdentityPlanNode.builder().uuid("uuid1").identifier("id1").stepType(StepType.newBuilder().build()).build();
    retriedNodeExecutions.add(NodeExecution.builder().planNode(node).uuid(retryId1).build());
    retriedNodeExecutions.add(NodeExecution.builder().planNode(node).uuid(retryId2).build());
    doReturn(retriedNodeExecutions).when(nodeExecutionService).getAll(any());
    doReturn(node).when(planService).fetchNode(eq("uuid1"));

    ArgumentCaptor<List> nodeExecutionArgumentCaptor = ArgumentCaptor.forClass(List.class);

    identityNodeExecutionStrategyHelper.copyNodeExecutionsForRetriedNodes(nodeExecution, retryIdsList);

    verify(nodeExecutionService, times(1)).saveAll(nodeExecutionArgumentCaptor.capture());
    verify(nodeExecutionService, times(1)).updateV2(eq(nodeExecution.getUuid()), any());

    List<NodeExecution> newNodeExecutions = nodeExecutionArgumentCaptor.getValue();
    assertEquals(newNodeExecutions.size(), retryIdsList.size());
    assertThat(retryIdsList.contains(newNodeExecutions.get(0).getOriginalNodeExecutionId())).isTrue();
    assertThat(retryIdsList.contains(newNodeExecutions.get(1).getOriginalNodeExecutionId())).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetMappedRetryIdsForNewNodeExecution() {
    List<String> retryIds = List.of("retryId1", "retryId2");
    Map<String, String> retryIdsMap =
        Map.of(retryIds.get(0), "mappedId1", retryIds.get(1), "mappedId2", "retryId3", "mappedId3");

    List<String> mappedIds =
        identityNodeExecutionStrategyHelper.getNewRetryIdsFromOriginalRetryIds(retryIds, retryIdsMap);
    assertEquals(mappedIds.size(), retryIds.size());
    assertThat(mappedIds.contains(retryIdsMap.get(retryIds.get(0)))).isTrue();
    assertThat(mappedIds.contains(retryIdsMap.get(retryIds.get(1)))).isTrue();
  }
}
