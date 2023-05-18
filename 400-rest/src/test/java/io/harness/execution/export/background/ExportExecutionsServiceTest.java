/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.background;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.CreatedByType;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExportExecutionsException;
import io.harness.execution.export.ExportExecutionsFileService;
import io.harness.execution.export.ExportExecutionsUtils;
import io.harness.execution.export.metadata.ExecutionMetadata;
import io.harness.execution.export.metadata.PipelineExecutionMetadata;
import io.harness.execution.export.metadata.WorkflowExecutionMetadata;
import io.harness.execution.export.processor.ExportExecutionsProcessor;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequestHelper;
import io.harness.execution.export.request.ExportExecutionsRequestService;
import io.harness.execution.export.request.RequestTestUtils;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ExportExecutionsServiceTest extends WingsBaseTest {
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ExportExecutionsRequestService exportExecutionsRequestService;
  @Mock private ExportExecutionsFileService exportExecutionsFileService;
  @Mock private ExportExecutionsRequestHelper exportExecutionsRequestHelper;
  @Inject @InjectMocks private ExportExecutionsService exportExecutionsService;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFailRequest() {
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    String errMsg = "errMsg";
    exportExecutionsService.failRequest(request, errMsg);
    verify(exportExecutionsRequestService, times(1)).failRequest(eq(request), eq(errMsg));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testExpireRequest() {
    ExportExecutionsRequest request =
        RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY);
    exportExecutionsService.expireRequest(request);
    verify(exportExecutionsRequestService, times(1)).expireRequest(eq(request));
  }

  @Test(expected = Test.None.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testExport() {
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    assertThatThrownBy(() -> exportExecutionsService.export(request)).isInstanceOf(ExportExecutionsException.class);

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(
            Collections.singletonList(WorkflowExecution.builder().workflowType(WorkflowType.ORCHESTRATION).build()))
        .thenReturn(Collections.emptyList());
    assertThatCode(() -> exportExecutionsService.export(request)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUpdateExportStream() {
    ZipOutputStream zipOutputStream = mock(ZipOutputStream.class);
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    assertThatThrownBy(() -> exportExecutionsService.updateExportStream(request, zipOutputStream))
        .isInstanceOf(ExportExecutionsException.class);

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(asList(WorkflowExecution.builder().workflowType(WorkflowType.ORCHESTRATION).build(),
            WorkflowExecution.builder()
                .workflowType(WorkflowType.PIPELINE)
                .pipelineExecution(
                    aPipelineExecution()
                        .withPipelineStageExecutions(Collections.singletonList(
                            PipelineStageExecution.builder()
                                .workflowExecutions(asList(WorkflowExecution.builder().uuid("id1").name("wf1").build(),
                                    WorkflowExecution.builder().uuid("id2").name("wf2").build()))
                                .build()))
                        .build())
                .build()))
        .thenReturn(asList(WorkflowExecution.builder().uuid("id1").name("wfn1").build(),
            WorkflowExecution.builder().uuid("id2").name("wfn2").build()))
        .thenReturn(Collections.emptyList());
    assertThatCode(() -> exportExecutionsService.updateExportStream(request, zipOutputStream))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUploadFile() {
    String fileId = "tmpFileId";
    when(exportExecutionsFileService.uploadFile(any(), any())).thenReturn(fileId);

    File file = mock(File.class);
    ExportExecutionsRequest request =
        RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY);
    exportExecutionsService.uploadFile(request, file);
    verify(exportExecutionsRequestService, times(1)).readyRequest(request, fileId);

    doThrow(new RuntimeException("")).when(exportExecutionsRequestService).readyRequest(any(), any());
    assertThatThrownBy(() -> exportExecutionsService.uploadFile(request, file)).isInstanceOf(RuntimeException.class);
    verify(exportExecutionsFileService, times(1)).deleteFile(fileId);

    doThrow(new RuntimeException("")).when(exportExecutionsFileService).deleteFile(any());
    assertThatThrownBy(() -> exportExecutionsService.uploadFile(request, file)).isInstanceOf(RuntimeException.class);
    verify(exportExecutionsFileService, times(2)).deleteFile(fileId);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testPrepareExecutionMetadataListBatch() {
    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true))).thenReturn(Collections.emptyList());

    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    assertThat(exportExecutionsService.prepareExecutionMetadataListBatch(request, 0)).isEmpty();

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(asList(WorkflowExecution.builder().workflowType(WorkflowType.ORCHESTRATION).build(),
            WorkflowExecution.builder()
                .workflowType(WorkflowType.PIPELINE)
                .pipelineExecution(
                    aPipelineExecution()
                        .withPipelineStageExecutions(Collections.singletonList(
                            PipelineStageExecution.builder()
                                .workflowExecutions(asList(WorkflowExecution.builder().uuid("id1").name("wf1").build(),
                                    WorkflowExecution.builder().uuid("id2").name("wf2").build()))
                                .build()))
                        .build())
                .build()))
        .thenReturn(asList(WorkflowExecution.builder().uuid("id1").name("wfn1").build(),
            WorkflowExecution.builder().uuid("id2").name("wfn2").build()));
    List<ExecutionMetadata> executionMetadataList =
        exportExecutionsService.prepareExecutionMetadataListBatch(request, 0);
    assertThat(executionMetadataList.size()).isEqualTo(2);
    assertThat(executionMetadataList.get(0)).isInstanceOf(WorkflowExecutionMetadata.class);
    assertThat(executionMetadataList.get(1)).isInstanceOf(PipelineExecutionMetadata.class);
  }

  private static class DummyProcessor implements ExportExecutionsProcessor {
    public final Set<String> ids = new HashSet<>();
    public boolean processCalled;

    @Override
    public void visitExecutionMetadata(ExecutionMetadata executionMetadata) {
      if (executionMetadata != null && executionMetadata.getId() != null) {
        ids.add(executionMetadata.getId());
      }
    }

    @Override
    public void process() {
      processCalled = true;
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testRunProcessors() {
    DummyProcessor processor1 = new DummyProcessor();
    DummyProcessor processor2 = new DummyProcessor();
    exportExecutionsService.runProcessors(Collections.emptyList(), processor1, processor2);
    assertThat(processor1.ids).isEmpty();
    assertThat(processor1.processCalled).isFalse();
    assertThat(processor2.ids).isEmpty();
    assertThat(processor2.processCalled).isFalse();

    exportExecutionsService.runProcessors(asList(WorkflowExecutionMetadata.builder().id("id1").build(),
                                              WorkflowExecutionMetadata.builder().id("id2").build()),
        processor1, processor2);
    assertThat(processor1.ids).containsExactlyInAnyOrder("id1", "id2");
    assertThat(processor1.processCalled).isTrue();
    assertThat(processor2.ids).containsExactlyInAnyOrder("id1", "id2");
    assertThat(processor2.processCalled).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcessExecutionMetadataList() throws IOException {
    ZipOutputStream zipOutputStream = mock(ZipOutputStream.class);
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    request.setCreatedBy(EmbeddedUser.builder().name("username").build());
    exportExecutionsService.processExecutionMetadataList(request, Collections.emptyList(), zipOutputStream);
    verify(zipOutputStream, never()).flush();

    exportExecutionsService.processExecutionMetadataList(request,
        asList(WorkflowExecutionMetadata.builder().id("id1").build(),
            WorkflowExecutionMetadata.builder().id("id2").build()),
        zipOutputStream);
    verify(zipOutputStream, times(1)).flush();
    verify(zipOutputStream, times(4)).putNextEntry(any());
    verify(zipOutputStream, times(4)).write(any());
    verify(zipOutputStream, times(4)).closeEntry();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testPrepareReadmeContent() {
    String downloadLink = "downloadLink";
    when(exportExecutionsRequestHelper.prepareDownloadLink(anyString(), anyString())).thenReturn(downloadLink);
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    request.setCreatedBy(EmbeddedUser.builder().name("username").build());
    request.setCreatedByType(CreatedByType.API_KEY);
    WorkflowExecutionMetadata workflowExecutionMetadata =
        WorkflowExecutionMetadata.builder().executionType("Workflow").entityName("wf").build();
    Set<String> logFiles = ImmutableSet.of("lf1.log", "lf2.log");
    byte[] bs = exportExecutionsService.prepareReadmeContent(request, workflowExecutionMetadata, logFiles, "json");
    String content = new String(bs, StandardCharsets.UTF_8);
    assertThat(content).isEqualTo(format("--------------------------------------------------\n"
            + "Export Information\n"
            + "--------------------------------------------------\n"
            + "Workflow name: wf\n"
            + "Export time: %s\n"
            + "Generated by: [API KEY] username\n"
            + "Download link: %s\n"
            + "Expires on: %s\n"
            + "--------------------------------------------------\n"
            + "Files attached to this export (3 files)\n"
            + "--------------------------------------------------\n"
            + "Workflow: execution.json\n"
            + "Logs:\n"
            + "lf1.log\n"
            + "lf2.log\n",
        exportExecutionsService.readmeDateTimeFormatter.format(
            ExportExecutionsUtils.prepareZonedDateTime(request.getCreatedAt())),
        downloadLink,
        exportExecutionsService.readmeDateTimeFormatter.format(
            ExportExecutionsUtils.prepareZonedDateTime(request.getExpiresAt()))));

    request.setExpiresAt(0);
    request.setCreatedAt(0);
    request.setCreatedBy(null);
    bs = exportExecutionsService.prepareReadmeContent(
        request, workflowExecutionMetadata, Collections.emptySet(), "json");
    content = new String(bs, StandardCharsets.UTF_8);
    assertThat(content).isEqualTo(format("--------------------------------------------------\n"
            + "Export Information\n"
            + "--------------------------------------------------\n"
            + "Workflow name: wf\n"
            + "Download link: %s\n"
            + "--------------------------------------------------\n"
            + "Files attached to this export (1 files)\n"
            + "--------------------------------------------------\n"
            + "Workflow: execution.json\n",
        downloadLink));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testProcessExportExecutionsRequestBatch() {
    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true))).thenReturn(Collections.emptyList());

    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    List<WorkflowExecution> workflowExecutions =
        exportExecutionsService.processExportExecutionsRequestBatch(request, 0);
    assertThat(workflowExecutions).isEmpty();

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(
            Collections.singletonList(WorkflowExecution.builder().workflowType(WorkflowType.ORCHESTRATION).build()));
    workflowExecutions = exportExecutionsService.processExportExecutionsRequestBatch(request, 0);
    assertThat(workflowExecutions.size()).isEqualTo(1);

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(Collections.singletonList(WorkflowExecution.builder().workflowType(WorkflowType.PIPELINE).build()));
    workflowExecutions = exportExecutionsService.processExportExecutionsRequestBatch(request, 0);
    assertThat(workflowExecutions.size()).isEqualTo(1);

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(Collections.singletonList(WorkflowExecution.builder()
                                                  .workflowType(WorkflowType.PIPELINE)
                                                  .pipelineExecution(aPipelineExecution().build())
                                                  .build()));
    workflowExecutions = exportExecutionsService.processExportExecutionsRequestBatch(request, 0);
    assertThat(workflowExecutions.size()).isEqualTo(1);

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(Collections.singletonList(
            WorkflowExecution.builder()
                .workflowType(WorkflowType.PIPELINE)
                .pipelineExecution(
                    aPipelineExecution()
                        .withPipelineStageExecutions(Collections.singletonList(
                            PipelineStageExecution.builder()
                                .workflowExecutions(asList(WorkflowExecution.builder().uuid("id1").build(),
                                    WorkflowExecution.builder().uuid("id2").build()))
                                .build()))
                        .build())
                .build()))
        .thenReturn(Collections.emptyList());
    workflowExecutions = exportExecutionsService.processExportExecutionsRequestBatch(request, 0);
    assertThat(workflowExecutions.size()).isEqualTo(1);

    when(workflowExecutionService.listExecutionsUsingQuery(any(), any(), eq(true)))
        .thenReturn(asList(WorkflowExecution.builder().workflowType(WorkflowType.ORCHESTRATION).build(),
            WorkflowExecution.builder()
                .workflowType(WorkflowType.PIPELINE)
                .pipelineExecution(
                    aPipelineExecution()
                        .withPipelineStageExecutions(Collections.singletonList(
                            PipelineStageExecution.builder()
                                .workflowExecutions(asList(WorkflowExecution.builder().uuid("id1").name("wf1").build(),
                                    WorkflowExecution.builder().uuid("id2").name("wf2").build()))
                                .build()))
                        .build())
                .build()))
        .thenReturn(asList(WorkflowExecution.builder().uuid("id1").name("wfn1").build(),
            WorkflowExecution.builder().uuid("id2").name("wfn2").build()));
    workflowExecutions = exportExecutionsService.processExportExecutionsRequestBatch(request, 0);
    assertThat(workflowExecutions.size()).isEqualTo(2);
    assertThat(workflowExecutions.get(0).getWorkflowType()).isEqualTo(WorkflowType.ORCHESTRATION);
    assertThat(workflowExecutions.get(1)
                   .getPipelineExecution()
                   .getPipelineStageExecutions()
                   .get(0)
                   .getWorkflowExecutions()
                   .get(0)
                   .getName())
        .isEqualTo("wfn1");
    assertThat(workflowExecutions.get(1)
                   .getPipelineExecution()
                   .getPipelineStageExecutions()
                   .get(0)
                   .getWorkflowExecutions()
                   .get(1)
                   .getName())
        .isEqualTo("wfn2");
  }
}
