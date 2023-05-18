/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.processor;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.export.metadata.ExecutionInterruptMetadata;
import io.harness.execution.export.metadata.GraphNodeMetadata;
import io.harness.execution.export.metadata.WorkflowExecutionMetadata;
import io.harness.rule.Owner;

import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterruptEffect;
import software.wings.sm.ExecutionInterruptManager;
import software.wings.sm.StateExecutionInstance;

import java.time.Instant;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class StateExecutionInstanceProcessorTest extends CategoryTest {
  @Mock private StateExecutionService stateExecutionService;
  @Mock private ExecutionInterruptManager executionInterruptManager;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testVisitExecutionMetadata() {
    StateExecutionInstanceProcessor stateExecutionInstanceProcessor = new StateExecutionInstanceProcessor();

    stateExecutionInstanceProcessor.visitExecutionMetadata(WorkflowExecutionMetadata.builder().id("id").build());
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isEmpty();

    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(Collections.singletonList(GraphNodeMetadata.builder().id("nid").build()))
            .build());
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isEmpty();

    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(
                Collections.singletonList(GraphNodeMetadata.builder().id("nid1").interruptHistoryCount(1).build()))
            .build());
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isNotEmpty();
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap().keySet())
        .containsExactlyInAnyOrder("nid1");

    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(
                Collections.singletonList(GraphNodeMetadata.builder().id("nid2").executionHistoryCount(1).build()))
            .build());
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isNotEmpty();
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap().keySet())
        .containsExactlyInAnyOrder("nid1", "nid2");

    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(Collections.singletonList(
                GraphNodeMetadata.builder().id("nid3").interruptHistoryCount(1).executionHistoryCount(1).build()))
            .build());
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isNotEmpty();
    assertThat(stateExecutionInstanceProcessor.getStateExecutionInstanceIdToNodeMetadataMap().keySet())
        .containsExactlyInAnyOrder("nid1", "nid2", "nid3");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcess() {
    StateExecutionInstanceProcessor stateExecutionInstanceProcessor = new StateExecutionInstanceProcessor();
    stateExecutionInstanceProcessor.setStateExecutionService(stateExecutionService);
    stateExecutionInstanceProcessor.setExecutionInterruptManager(executionInterruptManager);

    stateExecutionInstanceProcessor.process();
    verify(stateExecutionService, never()).listByIdsUsingSecondary(any());
    verify(executionInterruptManager, never()).listByStateExecutionIdsUsingSecondary(any());
    verify(executionInterruptManager, never()).listByIdsUsingSecondary(any());

    GraphNodeMetadata nodeMetadata =
        GraphNodeMetadata.builder().id("nid").interruptHistoryCount(1).executionHistoryCount(1).build();
    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder().id("id").executionGraph(Collections.singletonList(nodeMetadata)).build());

    stateExecutionInstanceProcessor.process();
    verify(stateExecutionService, times(1)).listByIdsUsingSecondary(any());
    verify(executionInterruptManager, times(1)).listByStateExecutionIdsUsingSecondary(any());
    verify(executionInterruptManager, never()).listByIdsUsingSecondary(any());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateInterruptRefsAndExecutionHistory() {
    StateExecutionInstanceProcessor stateExecutionInstanceProcessor = new StateExecutionInstanceProcessor();
    stateExecutionInstanceProcessor.setStateExecutionService(stateExecutionService);
    stateExecutionInstanceProcessor.setExecutionInterruptManager(executionInterruptManager);

    GraphNodeMetadata nodeMetadata =
        GraphNodeMetadata.builder().id("nid").interruptHistoryCount(1).executionHistoryCount(1).build();
    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder().id("id").executionGraph(Collections.singletonList(nodeMetadata)).build());

    when(stateExecutionService.listByIdsUsingSecondary(any())).thenReturn(Collections.emptyList());
    stateExecutionInstanceProcessor.updateInterruptRefsAndExecutionHistory();
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToNodeMetadataMap()).isEmpty();
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToInterruptEffectMap()).isEmpty();

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance()
            .uuid("nid")
            .stateExecutionDataHistory(Collections.singletonList(aStateExecutionData().build()))
            .build();

    when(stateExecutionService.listByIdsUsingSecondary(any()))
        .thenReturn(asList(stateExecutionInstance, aStateExecutionInstance().uuid("random").build()));
    stateExecutionInstanceProcessor.updateInterruptRefsAndExecutionHistory();
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToNodeMetadataMap()).isEmpty();
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToInterruptEffectMap()).isEmpty();
    assertThat(nodeMetadata.getExecutionHistory()).isNotNull();
    assertThat(nodeMetadata.getExecutionHistory().size()).isEqualTo(1);

    ExecutionInterruptEffect executionInterruptEffect = ExecutionInterruptEffect.builder().interruptId("iid").build();
    stateExecutionInstance.setInterruptHistory(
        asList(ExecutionInterruptEffect.builder().build(), executionInterruptEffect));
    when(stateExecutionService.listByIdsUsingSecondary(any()))
        .thenReturn(Collections.singletonList(stateExecutionInstance));
    stateExecutionInstanceProcessor.updateInterruptRefsAndExecutionHistory();
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToNodeMetadataMap()).isNotEmpty();
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToNodeMetadataMap().size()).isEqualTo(1);
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToNodeMetadataMap().get("iid")).isEqualTo(nodeMetadata);
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToInterruptEffectMap()).isNotEmpty();
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToInterruptEffectMap().size()).isEqualTo(1);
    assertThat(stateExecutionInstanceProcessor.getInterruptIdToInterruptEffectMap().get("iid"))
        .isEqualTo(executionInterruptEffect);
    assertThat(nodeMetadata.getExecutionHistory()).isNotNull();
    assertThat(nodeMetadata.getExecutionHistory().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateStateExecutionInstanceInterrupts() {
    StateExecutionInstanceProcessor stateExecutionInstanceProcessor = new StateExecutionInstanceProcessor();
    stateExecutionInstanceProcessor.setStateExecutionService(stateExecutionService);
    stateExecutionInstanceProcessor.setExecutionInterruptManager(executionInterruptManager);

    GraphNodeMetadata nodeMetadata =
        GraphNodeMetadata.builder().id("nid").interruptHistoryCount(1).executionHistoryCount(1).build();
    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder().id("id").executionGraph(Collections.singletonList(nodeMetadata)).build());

    when(executionInterruptManager.listByStateExecutionIdsUsingSecondary(any())).thenReturn(Collections.emptyList());
    stateExecutionInstanceProcessor.updateStateExecutionInstanceInterrupts();
    assertThat(nodeMetadata.getInterruptHistory()).isNull();

    Instant now = Instant.now();
    ExecutionInterrupt executionInterrupt1 =
        anExecutionInterrupt().uuid("iid1").stateExecutionInstanceId("nid").createdAt(now.toEpochMilli()).build();
    ExecutionInterrupt executionInterrupt2 =
        anExecutionInterrupt().uuid("iid2").stateExecutionInstanceId("nid").build();
    when(executionInterruptManager.listByStateExecutionIdsUsingSecondary(any()))
        .thenReturn(asList(executionInterrupt1, executionInterrupt2,
            anExecutionInterrupt().stateExecutionInstanceId("random").build()));
    stateExecutionInstanceProcessor.updateStateExecutionInstanceInterrupts();
    assertThat(nodeMetadata.getInterruptHistory()).isNotEmpty();
    assertThat(
        nodeMetadata.getInterruptHistory().stream().map(ExecutionInterruptMetadata::getId).collect(Collectors.toList()))
        .containsExactly("iid2", "iid1");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateIdInterrupts() {
    StateExecutionInstanceProcessor stateExecutionInstanceProcessor = new StateExecutionInstanceProcessor();
    stateExecutionInstanceProcessor.setStateExecutionService(stateExecutionService);
    stateExecutionInstanceProcessor.setExecutionInterruptManager(executionInterruptManager);

    stateExecutionInstanceProcessor.updateIdInterrupts();
    verify(executionInterruptManager, never()).listByIdsUsingSecondary(any());

    GraphNodeMetadata nodeMetadata =
        GraphNodeMetadata.builder().id("nid").interruptHistoryCount(1).executionHistoryCount(1).build();
    stateExecutionInstanceProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder().id("id").executionGraph(Collections.singletonList(nodeMetadata)).build());

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance()
            .uuid("nid")
            .stateExecutionDataHistory(Collections.singletonList(aStateExecutionData().build()))
            .build();
    when(stateExecutionService.listByIdsUsingSecondary(any()))
        .thenReturn(Collections.singletonList(stateExecutionInstance));

    ExecutionInterruptEffect executionInterruptEffect1 = ExecutionInterruptEffect.builder().interruptId("iid1").build();
    ExecutionInterruptEffect executionInterruptEffect2 = ExecutionInterruptEffect.builder().interruptId("iid2").build();
    stateExecutionInstance.setInterruptHistory(asList(executionInterruptEffect1, executionInterruptEffect2));
    when(stateExecutionService.listByIdsUsingSecondary(any()))
        .thenReturn(Collections.singletonList(stateExecutionInstance));
    stateExecutionInstanceProcessor.updateInterruptRefsAndExecutionHistory();

    when(executionInterruptManager.listByIdsUsingSecondary(any())).thenReturn(Collections.emptyList());
    stateExecutionInstanceProcessor.updateIdInterrupts();
    verify(executionInterruptManager, times(1)).listByIdsUsingSecondary(any());
    assertThat(nodeMetadata.getInterruptHistory()).isNull();

    Instant now = Instant.now();
    ExecutionInterrupt executionInterrupt1 =
        anExecutionInterrupt().uuid("iid1").stateExecutionInstanceId("nid").createdAt(now.toEpochMilli()).build();
    ExecutionInterrupt executionInterrupt2 =
        anExecutionInterrupt().uuid("iid2").stateExecutionInstanceId("nid").build();

    when(executionInterruptManager.listByIdsUsingSecondary(any()))
        .thenReturn(asList(executionInterrupt1, executionInterrupt2, anExecutionInterrupt().uuid("random").build()));
    stateExecutionInstanceProcessor.updateIdInterrupts();
    assertThat(nodeMetadata.getInterruptHistory()).isNotNull();
    assertThat(
        nodeMetadata.getInterruptHistory().stream().map(ExecutionInterruptMetadata::getId).collect(Collectors.toList()))
        .containsExactly("iid1", "iid2");
  }
}
