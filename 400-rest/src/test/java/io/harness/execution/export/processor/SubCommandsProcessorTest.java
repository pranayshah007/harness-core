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
import io.harness.execution.export.metadata.ActivityCommandUnitMetadata;
import io.harness.execution.export.metadata.ApprovalMetadata;
import io.harness.execution.export.metadata.ExecutionHistoryMetadata;
import io.harness.execution.export.metadata.GraphNodeMetadata;
import io.harness.execution.export.metadata.PipelineExecutionMetadata;
import io.harness.execution.export.metadata.PipelineStageExecutionMetadata;
import io.harness.execution.export.metadata.WorkflowExecutionMetadata;
import io.harness.rule.Owner;

import software.wings.beans.command.CommandUnitDetails;
import software.wings.service.intfc.ActivityService;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SubCommandsProcessorTest extends CategoryTest {
  @Mock private ActivityService activityService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testVisitExecutionMetadata() {
    SubCommandsProcessor subCommandsProcessor = new SubCommandsProcessor();

    subCommandsProcessor.visitExecutionMetadata(WorkflowExecutionMetadata.builder().id("id").build());
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap()).isEmpty();

    subCommandsProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(Collections.singletonList(GraphNodeMetadata.builder().id("nid").build()))
            .build());
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap()).isEmpty();

    subCommandsProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(Collections.singletonList(GraphNodeMetadata.builder().id("nid").activityId("aid").build()))
            .build());
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap()).isNotEmpty();
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap().keySet()).containsExactly("aid");

    subCommandsProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(Collections.singletonList(
                GraphNodeMetadata.builder()
                    .id("nid")
                    .activityId("aid")
                    .executionHistory(Collections.singletonList(ExecutionHistoryMetadata.builder().build()))
                    .build()))
            .build());
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap()).isNotEmpty();
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap().keySet()).containsExactly("aid");

    subCommandsProcessor.visitExecutionMetadata(
        PipelineExecutionMetadata.builder()
            .id("pid")
            .stages(asList(
                PipelineStageExecutionMetadata.builder().approvalData(ApprovalMetadata.builder().build()).build(),
                PipelineStageExecutionMetadata.builder()
                    .approvalData(ApprovalMetadata.builder().activityId("aid1").build())
                    .build(),
                PipelineStageExecutionMetadata.builder()
                    .workflowExecution(WorkflowExecutionMetadata.builder()
                                           .id("id")
                                           .executionGraph(Collections.singletonList(
                                               GraphNodeMetadata.builder()
                                                   .id("nid")
                                                   .activityId("aid2")
                                                   .executionHistory(Collections.singletonList(
                                                       ExecutionHistoryMetadata.builder().activityId("aid3").build()))
                                                   .build()))
                                           .build())
                    .build()))
            .build());
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap()).isNotEmpty();
    assertThat(subCommandsProcessor.getActivityIdToNodeMetadataMap().keySet())
        .containsExactlyInAnyOrder("aid", "aid1", "aid2", "aid3");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcess() {
    SubCommandsProcessor subCommandsProcessor = new SubCommandsProcessor();
    subCommandsProcessor.setActivityService(activityService);

    subCommandsProcessor.process();
    verify(activityService, never()).getCommandUnitsMapUsingSecondary(any());

    ExecutionHistoryMetadata executionHistoryMetadata1 = ExecutionHistoryMetadata.builder().activityId("aid2").build();
    ExecutionHistoryMetadata executionHistoryMetadata2 = ExecutionHistoryMetadata.builder().activityId("aid3").build();
    GraphNodeMetadata nodeMetadata = GraphNodeMetadata.builder()
                                         .id("nid")
                                         .activityId("aid1")
                                         .executionHistory(Collections.singletonList(executionHistoryMetadata1))
                                         .build();
    subCommandsProcessor.visitExecutionMetadata(
        WorkflowExecutionMetadata.builder().id("id").executionGraph(Collections.singletonList(nodeMetadata)).build());

    when(activityService.getCommandUnitsMapUsingSecondary(any())).thenReturn(Collections.emptyMap());
    subCommandsProcessor.process();
    verify(activityService, times(1)).getCommandUnitsMapUsingSecondary(any());

    when(activityService.getCommandUnitsMapUsingSecondary(any()))
        .thenReturn(ImmutableMap.of("aid1",
            asList(CommandUnitDetails.builder().name("cu1").build(), CommandUnitDetails.builder().name("cu2").build()),
            "aid2", Collections.singletonList(CommandUnitDetails.builder().name("cu1").build()), "random",
            Collections.singletonList(CommandUnitDetails.builder().name("cu1").build())));
    subCommandsProcessor.process();
    verify(activityService, times(2)).getCommandUnitsMapUsingSecondary(any());
    assertThat(
        nodeMetadata.getSubCommands().stream().map(ActivityCommandUnitMetadata::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("cu1", "cu2");
    assertThat(executionHistoryMetadata1.getSubCommands()
                   .stream()
                   .map(ActivityCommandUnitMetadata::getName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("cu1");
    assertThat(executionHistoryMetadata2.getSubCommands()).isNull();
  }
}
