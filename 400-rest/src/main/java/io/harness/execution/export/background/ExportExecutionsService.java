/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.background;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CreatedByType;
import io.harness.beans.WorkflowType;
import io.harness.exception.ExportExecutionsException;
import io.harness.execution.export.ExportExecutionsFileService;
import io.harness.execution.export.ExportExecutionsUtils;
import io.harness.execution.export.formatter.OutputFormatter;
import io.harness.execution.export.metadata.ExecutionMetadata;
import io.harness.execution.export.metadata.PipelineExecutionMetadata;
import io.harness.execution.export.metadata.WorkflowExecutionMetadata;
import io.harness.execution.export.processor.ActivityLogsProcessor;
import io.harness.execution.export.processor.ExportExecutionsProcessor;
import io.harness.execution.export.processor.StateExecutionInstanceProcessor;
import io.harness.execution.export.processor.StateInspectionProcessor;
import io.harness.execution.export.processor.SubCommandsProcessor;
import io.harness.execution.export.processor.UserGroupsProcessor;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequestHelper;
import io.harness.execution.export.request.ExportExecutionsRequestQuery;
import io.harness.execution.export.request.ExportExecutionsRequestService;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.CreatedAtAware;

import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ExportExecutionsService {
  private static final String BASE_FOLDER_NAME = "HarnessExecutionsLogs";
  private static final String README_SEPARATOR = "--------------------------------------------------\n";

  private static final int EXPORT_EXECUTIONS_BATCH_SIZE = 20;
  private static final int POST_PROCESSING_BATCH_SIZE = 10;

  final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss-SSS");
  final DateTimeFormatter readmeDateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a z");

  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private Injector injector;

  @Inject private ExportExecutionsRequestService exportExecutionsRequestService;
  @Inject private ExportExecutionsFileService exportExecutionsFileService;
  @Inject private ExportExecutionsRequestHelper exportExecutionsRequestHelper;

  void failRequest(@NotNull ExportExecutionsRequest request, String errorMessage) {
    exportExecutionsRequestService.failRequest(request, errorMessage);
  }

  void expireRequest(@NotNull ExportExecutionsRequest request) {
    String fileId = exportExecutionsRequestService.expireRequest(request);
    exportExecutionsFileService.deleteFile(fileId);
  }

  void export(@NotNull ExportExecutionsRequest request) {
    String accountId = request.getAccountId();
    try {
      File zipFile = File.createTempFile(accountId + "_", ".zip");
      try (FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
           ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
        updateExportStream(request, zipOutputStream);
      }

      uploadFile(request, zipFile);
    } catch (IOException ex) {
      throw new ExportExecutionsException("Unable to create zip file for export executions request", ex);
    }
  }

  @VisibleForTesting
  void updateExportStream(@NotNull ExportExecutionsRequest request, @NotNull ZipOutputStream zipOutputStream)
      throws IOException {
    for (int currPageOffset = 0;; currPageOffset++) {
      List<ExecutionMetadata> executionMetadataList = prepareExecutionMetadataListBatch(request, currPageOffset);
      if (isEmpty(executionMetadataList)) {
        if (currPageOffset == 0) {
          throw new ExportExecutionsException(
              "No executions found matching request filters. Note that executions that were not finished when request was made are ignored");
        }
        break;
      }

      processExecutionMetadataList(request, executionMetadataList, zipOutputStream);
    }
  }

  @VisibleForTesting
  void uploadFile(@NotNull ExportExecutionsRequest request, @NotNull File file) {
    String fileId = exportExecutionsFileService.uploadFile(request.getAccountId(), file);
    try {
      exportExecutionsRequestService.readyRequest(request, fileId);
    } catch (Exception ex) {
      try {
        exportExecutionsFileService.deleteFile(fileId);
      } catch (Exception ex1) {
        // Ignore the exception as we are about to throw the relevant exception in the next line.
        log.error("Unable to delete file for export executions request", ex1);
      }

      throw ex;
    }
  }

  @VisibleForTesting
  List<ExecutionMetadata> prepareExecutionMetadataListBatch(
      @NotNull ExportExecutionsRequest request, int currPageOffset) {
    List<WorkflowExecution> workflowExecutions = processExportExecutionsRequestBatch(request, currPageOffset);
    List<ExecutionMetadata> currExecutionMetadataList = prepareExecutionMetadataList(workflowExecutions);
    if (isEmpty(currExecutionMetadataList)) {
      return Collections.emptyList();
    }

    runProcessors(currExecutionMetadataList, new UserGroupsProcessor(), new StateInspectionProcessor(),
        new StateExecutionInstanceProcessor(), new SubCommandsProcessor());
    return currExecutionMetadataList;
  }

  private List<ExecutionMetadata> prepareExecutionMetadataList(List<WorkflowExecution> workflowExecutions) {
    if (isEmpty(workflowExecutions)) {
      return Collections.emptyList();
    }

    return workflowExecutions.stream().map(this::prepareExecutionMetadata).collect(Collectors.toList());
  }

  private ExecutionMetadata prepareExecutionMetadata(WorkflowExecution workflowExecution) {
    if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
      return PipelineExecutionMetadata.fromWorkflowExecution(workflowExecution);
    } else {
      return WorkflowExecutionMetadata.fromWorkflowExecution(workflowExecution);
    }
  }

  @VisibleForTesting
  void runProcessors(List<ExecutionMetadata> executionMetadataList, ExportExecutionsProcessor... processors) {
    if (isEmpty(executionMetadataList) || isEmpty(processors)) {
      return;
    }

    for (ExportExecutionsProcessor processor : processors) {
      injector.injectMembers(processor);
      executionMetadataList.forEach(processor::visitExecutionMetadata);
      processor.process();
    }
  }

  @VisibleForTesting
  void processExecutionMetadataList(@NotNull ExportExecutionsRequest request,
      @NotEmpty List<ExecutionMetadata> executionMetadataList, @NotNull ZipOutputStream zipOutputStream)
      throws IOException {
    OutputFormatter outputFormatter = OutputFormatter.fromOutputFormat(request.getOutputFormat());
    for (int startIdx = 0; startIdx < executionMetadataList.size(); startIdx += POST_PROCESSING_BATCH_SIZE) {
      int endIdx = Math.min(startIdx + POST_PROCESSING_BATCH_SIZE, executionMetadataList.size());
      processExecutionMetadataListBatch(
          request, executionMetadataList.subList(startIdx, endIdx), outputFormatter, zipOutputStream);
      zipOutputStream.flush();
    }
  }

  private void processExecutionMetadataListBatch(@NotNull ExportExecutionsRequest request,
      List<ExecutionMetadata> executionMetadataList, OutputFormatter outputFormatter, ZipOutputStream zipOutputStream)
      throws IOException {
    if (isEmpty(executionMetadataList)) {
      return;
    }

    Map<String, String> folderNamesMap = new HashMap<>();
    for (ExecutionMetadata executionMetadata : executionMetadataList) {
      folderNamesMap.put(executionMetadata.getId(), BASE_FOLDER_NAME + "/" + prepareFolderName(executionMetadata));
    }

    Map<String, Set<String>> executionLogFilesMap = new HashMap<>();
    runProcessors(
        executionMetadataList, new ActivityLogsProcessor(zipOutputStream, folderNamesMap, executionLogFilesMap));

    for (ExecutionMetadata executionMetadata : executionMetadataList) {
      String folderName = folderNamesMap.get(executionMetadata.getId());
      zipOutputStream.putNextEntry(new ZipEntry(folderName + "/execution." + outputFormatter.getExtension()));
      zipOutputStream.write(outputFormatter.getExecutionMetadataOutputBytes(executionMetadata));
      zipOutputStream.closeEntry();

      zipOutputStream.putNextEntry(new ZipEntry(folderName + "/README.txt"));
      zipOutputStream.write(prepareReadmeContent(request, executionMetadata,
          executionLogFilesMap.get(executionMetadata.getId()), outputFormatter.getExtension()));
      zipOutputStream.closeEntry();
    }
  }

  @VisibleForTesting
  byte[] prepareReadmeContent(@NotNull ExportExecutionsRequest request, ExecutionMetadata executionMetadata,
      Set<String> logFiles, String outputFormatExtension) {
    if (logFiles == null) {
      logFiles = Collections.emptySet();
    }

    StringBuilder sb = new StringBuilder(128)
                           .append(README_SEPARATOR)
                           .append("Export Information\n")
                           .append(README_SEPARATOR)
                           .append(executionMetadata.getExecutionType())
                           .append(" name: ")
                           .append(executionMetadata.getEntityName())
                           .append('\n');
    ZonedDateTime now = ExportExecutionsUtils.prepareZonedDateTime(request.getCreatedAt());
    if (now != null) {
      sb.append("Export time: ").append(readmeDateTimeFormatter.format(now)).append('\n');
    }

    if (request.getCreatedBy() != null && request.getCreatedBy().getName() != null) {
      sb.append("Generated by: ");
      if (request.getCreatedByType() == CreatedByType.API_KEY) {
        sb.append("[API KEY] ");
      }
      sb.append(request.getCreatedBy().getName()).append('\n');
    }

    sb.append("Download link: ")
        .append(exportExecutionsRequestHelper.prepareDownloadLink(request.getAccountId(), request.getUuid()))
        .append('\n');

    ZonedDateTime expiresAt = ExportExecutionsUtils.prepareZonedDateTime(request.getExpiresAt());
    if (expiresAt != null) {
      sb.append("Expires on: ").append(readmeDateTimeFormatter.format(expiresAt)).append('\n');
    }

    sb.append(README_SEPARATOR)
        .append("Files attached to this export (")
        .append(logFiles.size() + 1)
        .append(" files)\n")
        .append(README_SEPARATOR)
        .append(executionMetadata.getExecutionType())
        .append(": execution.")
        .append(outputFormatExtension)
        .append('\n');

    if (!logFiles.isEmpty()) {
      sb.append("Logs:\n");
      for (String logFile : logFiles) {
        sb.append(logFile).append('\n');
      }
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String prepareFolderName(ExecutionMetadata executionMetadata) {
    StringBuilder sb = new StringBuilder();
    sb.append(executionMetadata.getApplication()).append('_').append(executionMetadata.getEntityName()).append('_');
    if (executionMetadata.getTiming() != null && executionMetadata.getTiming().getStartTime() != null) {
      sb.append(dateTimeFormatter.format(executionMetadata.getTiming().getStartTime())).append('_');
    }

    return sb.append(executionMetadata.getId()).append(RandomStringUtils.randomAlphanumeric(4)).toString();
  }

  @VisibleForTesting
  List<WorkflowExecution> processExportExecutionsRequestBatch(
      @NotNull ExportExecutionsRequest request, int pageOffset) {
    Query<WorkflowExecution> query = wingsPersistence.createAnalyticsQuery(WorkflowExecution.class, excludeAuthority);
    ExportExecutionsRequestQuery.updateQuery(query, request.getQuery());
    query = query.order(Sort.descending(CreatedAtAware.CREATED_AT_KEY));
    List<WorkflowExecution> workflowExecutions = workflowExecutionService.listExecutionsUsingQuery(query,
        new FindOptions()
            .limit(EXPORT_EXECUTIONS_BATCH_SIZE)
            .skip(pageOffset * EXPORT_EXECUTIONS_BATCH_SIZE)
            .readPreference(ReadPreference.secondaryPreferred()),
        true);
    if (isEmpty(workflowExecutions)) {
      return Collections.emptyList();
    }

    Set<String> newWorkflowExecutionIds = collectSubWorkflowExecutionIds(workflowExecutions);
    if (isNotEmpty(newWorkflowExecutionIds)) {
      List<WorkflowExecution> subWorkflowExecutions = workflowExecutionService.listExecutionsUsingQuery(
          wingsPersistence.createAnalyticsQuery(WorkflowExecution.class)
              .filter(WorkflowExecutionKeys.accountId, request.getAccountId())
              .field(WorkflowExecutionKeys.uuid)
              .in(newWorkflowExecutionIds),
          new FindOptions().readPreference(ReadPreference.secondaryPreferred()), true);
      substituteSubWorkflowExecutionIds(workflowExecutions, subWorkflowExecutions);
    }

    return workflowExecutions;
  }

  private Set<String> collectSubWorkflowExecutionIds(List<WorkflowExecution> workflowExecutions) {
    Set<String> newWorkflowExecutionIds = new HashSet<>();
    for (WorkflowExecution workflowExecution : workflowExecutions) {
      if (workflowExecution.getWorkflowType() != WorkflowType.PIPELINE
          || workflowExecution.getPipelineExecution() == null
          || isEmpty(workflowExecution.getPipelineExecution().getPipelineStageExecutions())) {
        continue;
      }

      workflowExecution.getPipelineExecution()
          .getPipelineStageExecutions()
          .stream()
          .map(PipelineStageExecution::getWorkflowExecutions)
          .filter(Objects::nonNull)
          .flatMap(Collection::stream)
          .filter(Objects::nonNull)
          .map(WorkflowExecution::getUuid)
          .forEach(newWorkflowExecutionIds::add);
    }

    return newWorkflowExecutionIds;
  }

  private void substituteSubWorkflowExecutionIds(
      List<WorkflowExecution> workflowExecutions, List<WorkflowExecution> subWorkflowExecutions) {
    if (isEmpty(workflowExecutions) || isEmpty(subWorkflowExecutions)) {
      return;
    }

    Map<String, WorkflowExecution> subWorkflowExecutionMap =
        subWorkflowExecutions.stream().collect(Collectors.toMap(WorkflowExecution::getUuid, Function.identity()));

    for (WorkflowExecution workflowExecution : workflowExecutions) {
      if (workflowExecution.getWorkflowType() != WorkflowType.PIPELINE
          || workflowExecution.getPipelineExecution() == null
          || isEmpty(workflowExecution.getPipelineExecution().getPipelineStageExecutions())) {
        continue;
      }

      for (PipelineStageExecution stageExecution :
          workflowExecution.getPipelineExecution().getPipelineStageExecutions()) {
        if (isEmpty(stageExecution.getWorkflowExecutions())) {
          continue;
        }

        List<WorkflowExecution> newWorkflowExecutions =
            stageExecution.getWorkflowExecutions()
                .stream()
                .filter(Objects::nonNull)
                .map(execution -> {
                  if (subWorkflowExecutionMap.containsKey(execution.getUuid())) {
                    return subWorkflowExecutionMap.get(execution.getUuid());
                  }

                  return execution;
                })
                .collect(Collectors.toList());
        stageExecution.setWorkflowExecutions(newWorkflowExecutions);
      }
    }
  }
}
