/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.processor;

import static io.harness.rule.OwnerRule.GARVIT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.export.metadata.GraphNodeMetadata;
import io.harness.execution.export.metadata.WorkflowExecutionMetadata;
import io.harness.rule.Owner;
import io.harness.state.inspection.ExpressionVariableUsage;
import io.harness.state.inspection.StateInspection;
import io.harness.state.inspection.StateInspectionData;
import io.harness.state.inspection.StateInspectionService;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class StateInspectionProcessorTest extends CategoryTest {
  @Mock private StateInspectionService stateInspectionService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testVisitExecutionMetadata() {
    StateInspectionProcessor stateInspectionProcessor = new StateInspectionProcessor();

    stateInspectionProcessor.visitExecutionMetadata(WorkflowExecutionMetadata.builder().id("id").build());
    assertThat(stateInspectionProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isEmpty();
    stateInspectionProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder().id("id").executionGraph(Collections.emptyList()).build());
    assertThat(stateInspectionProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isEmpty();
    stateInspectionProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(Collections.singletonList(GraphNodeMetadata.builder().build()))
            .build());
    assertThat(stateInspectionProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isEmpty();
    stateInspectionProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(Collections.singletonList(GraphNodeMetadata.builder().id("nid").build()))
            .build());
    assertThat(stateInspectionProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isEmpty();

    stateInspectionProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(
                Collections.singletonList(GraphNodeMetadata.builder().id("nid").hasInspection(true).build()))
            .build());
    assertThat(stateInspectionProcessor.getStateExecutionInstanceIdToNodeMetadataMap()).isNotEmpty();
    assertThat(stateInspectionProcessor.getStateExecutionInstanceIdToNodeMetadataMap().keySet())
        .containsExactlyInAnyOrder("nid");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcess() {
    StateInspectionProcessor stateInspectionProcessor = new StateInspectionProcessor();
    stateInspectionProcessor.setStateInspectionService(stateInspectionService);

    stateInspectionProcessor.process();
    verify(stateInspectionService, never()).listUsingSecondary(any());

    GraphNodeMetadata nodeMetadata1 = GraphNodeMetadata.builder().id("nid1").hasInspection(true).build();
    GraphNodeMetadata nodeMetadata2 = GraphNodeMetadata.builder().id("nid2").build();
    GraphNodeMetadata nodeMetadata3 = GraphNodeMetadata.builder().id("nid3").hasInspection(true).build();
    GraphNodeMetadata nodeMetadata4 = GraphNodeMetadata.builder().id("nid4").hasInspection(true).build();
    stateInspectionProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(asList(nodeMetadata1, nodeMetadata2, nodeMetadata3, nodeMetadata4))
            .build());

    when(stateInspectionService.listUsingSecondary(any())).thenReturn(Collections.emptyList());
    stateInspectionProcessor.process();
    verify(stateInspectionService, times(1)).listUsingSecondary(any());

    when(stateInspectionService.listUsingSecondary(any()))
        .thenReturn(asList(StateInspection.builder().stateExecutionInstanceId("random").build(),
            StateInspection.builder().stateExecutionInstanceId("nid1").data(prepareData("k1")).build(),
            StateInspection.builder().stateExecutionInstanceId("nid2").data(prepareData("k2")).build(),
            StateInspection.builder().stateExecutionInstanceId("nid3").data(prepareData("k3")).build(),
            StateInspection.builder().stateExecutionInstanceId("nid4").data(prepareData(null)).build()));
    stateInspectionProcessor.process();
    verify(stateInspectionService, times(2)).listUsingSecondary(any());
    assertThat(nodeMetadata1.getExecutionContext()
                   .stream()
                   .map(ExpressionVariableUsage.Item::getExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("k1");
    assertThat(nodeMetadata2.getExecutionContext()).isNull();
    assertThat(nodeMetadata3.getExecutionContext()
                   .stream()
                   .map(ExpressionVariableUsage.Item::getExpression)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("k3");
    assertThat(nodeMetadata4.getExecutionContext()).isNull();
  }

  private Map<String, StateInspectionData> prepareData(String key) {
    return ImmutableMap.of("expressionVariableUsage",
        ExpressionVariableUsage.builder()
            .variables(key == null
                    ? Collections.emptyList()
                    : Collections.singletonList(
                        ExpressionVariableUsage.Item.builder().expression(key).value("v").count(1).build()))
            .build());
  }
}
