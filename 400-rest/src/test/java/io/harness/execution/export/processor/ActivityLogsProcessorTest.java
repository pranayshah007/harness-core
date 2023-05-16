/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.processor;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.beans.Log.Builder.aLog;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExportExecutionsException;
import io.harness.execution.export.metadata.ActivityCommandUnitMetadata;
import io.harness.execution.export.metadata.ApprovalMetadata;
import io.harness.execution.export.metadata.ExecutionHistoryMetadata;
import io.harness.execution.export.metadata.GraphNodeMetadata;
import io.harness.execution.export.metadata.PipelineExecutionMetadata;
import io.harness.execution.export.metadata.PipelineStageExecutionMetadata;
import io.harness.execution.export.metadata.WorkflowExecutionMetadata;
import io.harness.execution.export.processor.ActivityLogsProcessor.ActivityIdsVisitor;
import io.harness.rule.Owner;

import software.wings.beans.Log;
import software.wings.beans.Log.LogKeys;
import software.wings.service.impl.MongoDataStoreServiceImpl;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.LogService;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ActivityLogsProcessorTest extends CategoryTest {
  @Mock private LogService logService;
  private DataStoreService dataStoreService;
  private ExecutorService gdsExecutorService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setup() {
    dataStoreService = mock(MongoDataStoreServiceImpl.class);
    gdsExecutorService = Executors.newFixedThreadPool(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testVisitExecutionMetadata() {
    ActivityLogsProcessor activityLogsProcessor = new ActivityLogsProcessor(null, null, new HashMap<>());

    activityLogsProcessor.visitExecutionMetadata(WorkflowExecutionMetadata.builder().id("id").build());
    assertThat(activityLogsProcessor.getActivityIdToExecutionDetailsMap()).isEmpty();
    assertThat(activityLogsProcessor.getActivityIdToExecutionMap()).isEmpty();

    WorkflowExecutionMetadata workflowExecutionMetadata =
        WorkflowExecutionMetadata.builder()
            .id("id")
            .executionGraph(asList(
                GraphNodeMetadata.builder()
                    .activityId("id1")
                    .subCommands(Collections.singletonList(ActivityCommandUnitMetadata.builder().build()))
                    .executionHistory(Collections.singletonList(
                        ExecutionHistoryMetadata.builder()
                            .activityId("id2")
                            .subCommands(Collections.singletonList(ActivityCommandUnitMetadata.builder().build()))
                            .build()))
                    .build(),
                GraphNodeMetadata.builder()
                    .activityId("id3")
                    .subCommands(Collections.singletonList(ActivityCommandUnitMetadata.builder().build()))
                    .build()))
            .build();
    PipelineExecutionMetadata pipelineExecutionMetadata =
        PipelineExecutionMetadata.builder()
            .id("pid")
            .stages(asList(
                PipelineStageExecutionMetadata.builder().approvalData(ApprovalMetadata.builder().build()).build(),
                PipelineStageExecutionMetadata.builder()
                    .approvalData(ApprovalMetadata.builder().activityId("aid").build())
                    .build(),
                PipelineStageExecutionMetadata.builder().workflowExecution(workflowExecutionMetadata).build()))
            .build();
    activityLogsProcessor.visitExecutionMetadata(pipelineExecutionMetadata);
    assertThat(activityLogsProcessor.getActivityIdToExecutionDetailsMap().keySet())
        .containsExactlyInAnyOrder("aid", "id1", "id2", "id3");
    assertThat(activityLogsProcessor.getActivityIdToExecutionDetailsMap().get("aid"))
        .isEqualTo(pipelineExecutionMetadata.getStages().get(1).getApprovalData());
    assertThat(activityLogsProcessor.getActivityIdToExecutionDetailsMap().get("id1"))
        .isEqualTo(workflowExecutionMetadata.getExecutionGraph().get(0));
    assertThat(activityLogsProcessor.getActivityIdToExecutionDetailsMap().get("id2"))
        .isEqualTo(workflowExecutionMetadata.getExecutionGraph().get(0).getExecutionHistory().get(0));
    assertThat(activityLogsProcessor.getActivityIdToExecutionDetailsMap().get("id3"))
        .isEqualTo(workflowExecutionMetadata.getExecutionGraph().get(1));

    assertThat(activityLogsProcessor.getActivityIdToExecutionMap().keySet())
        .containsExactlyInAnyOrder("aid", "id1", "id2", "id3");
    assertThat(activityLogsProcessor.getActivityIdToExecutionMap().get("aid").getId()).isEqualTo("pid");
    assertThat(activityLogsProcessor.getActivityIdToExecutionMap().get("id1").getId()).isEqualTo("pid");
    assertThat(activityLogsProcessor.getActivityIdToExecutionMap().get("id2").getId()).isEqualTo("pid");
    assertThat(activityLogsProcessor.getActivityIdToExecutionMap().get("id3").getId()).isEqualTo("pid");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcess() throws IOException {
    ZipOutputStream zipOutputStream = mock(ZipOutputStream.class);
    ActivityLogsProcessor activityLogsProcessor =
        new ActivityLogsProcessor(zipOutputStream, ImmutableMap.of("id1", "fn1", "id2", "fn2"), new HashMap<>());
    setupProcessor(activityLogsProcessor);

    activityLogsProcessor.process();
    verify(logService, never()).list(anyString(), any());

    ActivityCommandUnitMetadata commandUnit11 = ActivityCommandUnitMetadata.builder().name("cu1").build();
    ActivityCommandUnitMetadata commandUnit12 = ActivityCommandUnitMetadata.builder().name("cu2").build();
    ActivityCommandUnitMetadata commandUnit21 = ActivityCommandUnitMetadata.builder().name("cu1").build();
    ActivityCommandUnitMetadata commandUnit22 = ActivityCommandUnitMetadata.builder().name("cu2").build();
    ActivityCommandUnitMetadata commandUnit31 = ActivityCommandUnitMetadata.builder().name("cu1").build();
    ActivityCommandUnitMetadata[] currCommandUnit = new ActivityCommandUnitMetadata[] {null};

    doAnswer(invocation -> {
      ZipEntry entry = invocation.getArgument(0, ZipEntry.class);
      String name = entry.getName().substring(4);
      if (name.equals(commandUnit11.getExecutionLogFile())) {
        currCommandUnit[0] = commandUnit11;
      } else if (name.equals(commandUnit12.getExecutionLogFile())) {
        currCommandUnit[0] = commandUnit12;
      } else if (name.equals(commandUnit21.getExecutionLogFile())) {
        currCommandUnit[0] = commandUnit21;
      } else if (name.equals(commandUnit22.getExecutionLogFile())) {
        currCommandUnit[0] = commandUnit22;
      } else if (name.equals(commandUnit31.getExecutionLogFile())) {
        currCommandUnit[0] = commandUnit31;
      }

      return null;
    })
        .when(zipOutputStream)
        .putNextEntry(any(ZipEntry.class));

    doAnswer(invocation -> {
      if (currCommandUnit[0] == null) {
        return null;
      }

      if (currCommandUnit[0].getExecutionLogFileContent() == null) {
        currCommandUnit[0].setExecutionLogFile("0");
      } else {
        currCommandUnit[0].setExecutionLogFile(
            String.valueOf(currCommandUnit[0].getExecutionLogFileContent().split("\n").length));
      }

      return null;
    })
        .when(zipOutputStream)
        .write(any());

    doAnswer(invocation -> {
      currCommandUnit[0] = null;
      return null;
    })
        .when(zipOutputStream)
        .closeEntry();

    WorkflowExecutionMetadata workflowExecutionMetadata =
        WorkflowExecutionMetadata.builder()
            .id("id1")
            .executionGraph(asList(
                GraphNodeMetadata.builder()
                    .activityId("aid1")
                    .subCommands(asList(commandUnit11, commandUnit12))
                    .executionHistory(Collections.singletonList(ExecutionHistoryMetadata.builder()
                                                                    .activityId("aid2")
                                                                    .subCommands(asList(commandUnit21, commandUnit22))
                                                                    .build()))
                    .build(),
                GraphNodeMetadata.builder()
                    .activityId("aid3")
                    .subCommands(Collections.singletonList(commandUnit31))
                    .build()))
            .build();

    activityLogsProcessor.visitExecutionMetadata(workflowExecutionMetadata);
    when(logService.list(anyString(), any())).thenReturn(aPageResponse().withResponse(Collections.emptyList()).build());
    activityLogsProcessor.process();
    verify(logService, times(1)).list(anyString(), any());

    when(logService.list(anyString(), any()))
        .thenReturn(aPageResponse()
                        .withResponse(asList(aLog().activityId("aid1").commandUnitName("cu1").build(),
                            aLog().activityId("aid2").commandUnitName("cu1").build(),
                            aLog().activityId("aid1").commandUnitName("cu1").build(),
                            aLog().activityId("aid1").commandUnitName("cu2").build(),
                            aLog().activityId("aid3").commandUnitName("cu1").build()))
                        .build())
        .thenReturn(aPageResponse().withResponse(Collections.emptyList()).build());

    activityLogsProcessor.process();
    verify(logService, times(2)).list(anyString(), any());
    assertThat(commandUnit11.getExecutionLogFile()).isEqualTo("2");
    assertThat(commandUnit12.getExecutionLogFile()).isEqualTo("1");
    assertThat(commandUnit21.getExecutionLogFile()).isEqualTo("1");
    assertThat(commandUnit22.getExecutionLogFile()).isNull();
    assertThat(commandUnit31.getExecutionLogFile()).isEqualTo("1");

    when(logService.list(anyString(), any()))
        .thenReturn(aPageResponse()
                        .withResponse(asList(aLog().activityId("aid1").commandUnitName("cu1").build(),
                            aLog().activityId("aid2").commandUnitName("cu1").build()))
                        .build())
        .thenReturn(aPageResponse().withResponse(Collections.emptyList()).build());

    doThrow(IOException.class).when(zipOutputStream).closeEntry();
    assertThatThrownBy(activityLogsProcessor::process).isInstanceOf(ExportExecutionsException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetAllLogs() {
    ZipOutputStream zipOutputStream = mock(ZipOutputStream.class);
    ActivityLogsProcessor activityLogsProcessor =
        new ActivityLogsProcessor(zipOutputStream, Collections.emptyMap(), new HashMap<>());
    setupProcessor(activityLogsProcessor);
    on(activityLogsProcessor)
        .set("activityIdToExecutionDetailsMap",
            ImmutableMap.of("aid1", WorkflowExecutionMetadata.builder().build(), "aid2",
                WorkflowExecutionMetadata.builder().build(), "aid3", WorkflowExecutionMetadata.builder().build()));
    on(activityLogsProcessor)
        .set("activityIdToExecutionMap",
            ImmutableMap.of("aid1", WorkflowExecutionMetadata.builder().appId("appId").build(), "aid2",
                WorkflowExecutionMetadata.builder().appId("appId").build(), "aid3",
                WorkflowExecutionMetadata.builder().appId("appId").build()));

    Map<String, List<Log>> map = ImmutableMap.of("aid1",
        asList(aLog().activityId("aid1").commandUnitName("cu11").build(),
            aLog().activityId("aid1").commandUnitName("cu12").build()),
        "aid2",
        asList(aLog().activityId("aid2").commandUnitName("cu21").build(),
            aLog().activityId("aid2").commandUnitName("cu22").build()),
        "aid3",
        asList(aLog().activityId("aid3").commandUnitName("cu31").build(),
            aLog().activityId("aid3").commandUnitName("cu32").build()));

    when(logService.list(anyString(), any())).thenAnswer(invocation -> {
      PageRequest<Log> pageRequest = invocation.getArgument(1, PageRequest.class);
      if (pageRequest == null
          || (EmptyPredicate.isNotEmpty(pageRequest.getOffset()) && !"0".equals(pageRequest.getOffset()))) {
        return aPageResponse().withResponse(Collections.emptyList()).build();
      }

      List<String> activityIds = new ArrayList<>();
      pageRequest.getFilters().forEach(filter -> {
        if (LogKeys.activityId.equals(filter.getFieldName())) {
          for (Object activityId : filter.getFieldValues()) {
            activityIds.add((String) activityId);
          }
        }
      });

      List<Log> logs = new ArrayList<>();
      for (String activityId : activityIds) {
        logs.addAll(map.getOrDefault(activityId, Collections.emptyList()));
      }
      return aPageResponse().withResponse(logs).build();
    });

    List<Log> logs = activityLogsProcessor.getAllLogs();
    assertThat(logs).isNotNull();
    assertThat(logs.size()).isEqualTo(6);

    when(dataStoreService.supportsInOperator()).thenReturn(false);
    List<Log> gdsLogs = activityLogsProcessor.getAllLogs();
    assertThat(gdsLogs).isNotNull();
    assertThat(gdsLogs.size()).isEqualTo(6);

    logs.sort(Comparator.comparing(Log::getCommandUnitName));
    gdsLogs.sort(Comparator.comparing(Log::getCommandUnitName));
    assertThat(logs).isEqualTo(gdsLogs);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testActivityIdsVisitor() {
    ActivityIdsVisitor activityIdsVisitor = new ActivityIdsVisitor();
    activityIdsVisitor.visitGraphNode(null);
    assertThat(activityIdsVisitor.getActivityIdToNodeMetadataMap()).isEmpty();

    activityIdsVisitor = new ActivityIdsVisitor();
    activityIdsVisitor.visitGraphNode(
        GraphNodeMetadata.builder()
            .activityId("id1")
            .subCommands(Collections.singletonList(ActivityCommandUnitMetadata.builder().build()))
            .build());
    assertThat(activityIdsVisitor.getActivityIdToNodeMetadataMap().keySet()).containsExactly("id1");

    activityIdsVisitor = new ActivityIdsVisitor();
    activityIdsVisitor.visitGraphNode(
        GraphNodeMetadata.builder()
            .activityId("id1")
            .subCommands(Collections.singletonList(ActivityCommandUnitMetadata.builder().build()))
            .executionHistory(asList(ExecutionHistoryMetadata.builder().build(), null,
                ExecutionHistoryMetadata.builder().activityId("id2").build(),
                ExecutionHistoryMetadata.builder()
                    .activityId("id3")
                    .subCommands(Collections.singletonList(ActivityCommandUnitMetadata.builder().build()))
                    .build()))
            .build());
    assertThat(activityIdsVisitor.getActivityIdToNodeMetadataMap().keySet()).containsExactly("id1", "id3");
  }

  private void setupProcessor(ActivityLogsProcessor processor) {
    processor.setLogService(logService);
    processor.setDataStoreService(dataStoreService);
    processor.setGdsExecutorService(gdsExecutorService);
    when(dataStoreService.supportsInOperator()).thenReturn(true);
  }
}
