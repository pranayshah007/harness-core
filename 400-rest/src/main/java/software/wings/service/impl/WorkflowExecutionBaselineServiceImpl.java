/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static software.wings.common.VerificationConstants.ML_RECORDS_TTL_MONTHS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter;
import io.harness.beans.WorkflowType;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.ValidUntilAccess;

import software.wings.beans.Base;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.baseline.WorkflowExecutionBaseline;
import software.wings.beans.baseline.WorkflowExecutionBaseline.WorkflowExecutionBaselineKeys;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.metrics.TimeSeriesDataRecord.TimeSeriesMetricRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.WorkflowExecutionBaselineService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 2/16/18.
 */
@Slf4j
@OwnedBy(HarnessTeam.CV)
public class WorkflowExecutionBaselineServiceImpl implements WorkflowExecutionBaselineService {
  public static final Date BASELINE_TTL = Date.from(OffsetDateTime.now().plusYears(ML_RECORDS_TTL_MONTHS).toInstant());

  @Inject private WingsPersistence wingsPersistence;
  @Inject private DataStoreService dataStoreService;

  @Override
  public void markBaseline(
      List<WorkflowExecutionBaseline> workflowExecutionBaselines, String executionId, boolean isBaseline) {
    Preconditions.checkState(!isEmpty(workflowExecutionBaselines));

    for (WorkflowExecutionBaseline workflowExecutionBaseline : workflowExecutionBaselines) {
      List<WorkflowExecutionBaseline> existingBaselines =
          wingsPersistence.createQuery(WorkflowExecutionBaseline.class)
              .filter(WorkflowExecutionKeys.appId, workflowExecutionBaseline.getAppId())
              .filter(WorkflowExecutionBaselineKeys.workflowId, workflowExecutionBaseline.getWorkflowId())
              .filter(WorkflowExecutionBaselineKeys.envId, workflowExecutionBaseline.getEnvId())
              .filter(WorkflowExecutionBaselineKeys.serviceId, workflowExecutionBaseline.getServiceId())
              .limit(NO_LIMIT)
              .asList();
      log.info("existing baselines {}", existingBaselines);
      if (!isEmpty(existingBaselines)) {
        Preconditions.checkState(
            existingBaselines.size() == 1, "found more than 1 baselines for " + workflowExecutionBaseline);
        WorkflowExecutionBaseline executionBaseline = existingBaselines.get(0);
        String workflowExecutionId = executionBaseline.getWorkflowExecutionId();
        log.info("marking {} to not to be baseline", workflowExecutionId);
        wingsPersistence.updateField(WorkflowExecution.class, workflowExecutionId, "isBaseline", false);

        // mark workflows in pipeline to be baseline
        log.info("marking workflow baseline {}", executionBaseline);
        markPipelineWorkflowBaselineIfNecessary(executionBaseline, false);
        if (isBaseline) {
          Map<String, Object> updates = new HashMap<>();
          updates.put("workflowExecutionId", workflowExecutionBaseline.getWorkflowExecutionId());
          if (!isEmpty(executionBaseline.getPipelineExecutionId())) {
            if (!isEmpty(workflowExecutionBaseline.getPipelineExecutionId())) {
              updates.put("pipelineExecutionId", workflowExecutionBaseline.getPipelineExecutionId());
            }
            updatePipeLineIfNecessary(executionBaseline.getPipelineExecutionId());
          }
          wingsPersistence.updateFields(WorkflowExecutionBaseline.class, executionBaseline.getUuid(), updates);
        } else {
          wingsPersistence.delete(WorkflowExecutionBaseline.class, executionBaseline.getUuid());
        }
      } else {
        Preconditions.checkState(isBaseline,
            "Unsetting baselines which were not marked as baseline before " + executionId
                + " baselines: " + workflowExecutionBaseline);
        wingsPersistence.save(workflowExecutionBaseline);
      }

      log.info("marking {} to be baseline", workflowExecutionBaseline);
      wingsPersistence.updateField(
          WorkflowExecution.class, workflowExecutionBaseline.getWorkflowExecutionId(), "isBaseline", isBaseline);
      // update metric and log data's ttl
      if (isBaseline) {
        updateDataAndAnalysisRecords(
            workflowExecutionBaseline.getWorkflowExecutionId(), workflowExecutionBaseline.getAppId());
      }
      markPipelineWorkflowBaselineIfNecessary(workflowExecutionBaseline, isBaseline);
    }

    updatePipeLineIfNecessary(executionId);
  }

  private void markPipelineWorkflowBaselineIfNecessary(WorkflowExecutionBaseline baseline, boolean isBaseline) {
    WorkflowExecution execution =
        wingsPersistence.getWithAppId(WorkflowExecution.class, baseline.getAppId(), baseline.getWorkflowExecutionId());
    // the workflow execution might have been deleted due to expiry.
    if (execution == null || isEmpty(execution.getPipelineExecutionId())) {
      return;
    }

    WorkflowExecution workflowExecution = isEmpty(baseline.getPipelineExecutionId())
        ? wingsPersistence.getWithAppId(
            WorkflowExecution.class, baseline.getAppId(), execution.getPipelineExecutionId())
        : wingsPersistence.getWithAppId(
            WorkflowExecution.class, baseline.getAppId(), baseline.getPipelineExecutionId());
    if (workflowExecution == null) {
      return;
    }

    PipelineExecution pipelineExecution = workflowExecution.getPipelineExecution();
    if (pipelineExecution == null) {
      return;
    }

    List<PipelineStageExecution> pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    if (isEmpty(pipelineStageExecutions)) {
      return;
    }

    pipelineStageExecutions.forEach(
        pipelineStageExecution -> pipelineStageExecution.getWorkflowExecutions().forEach(pipelineWorkflowExecution -> {
          if (pipelineWorkflowExecution.getUuid().equals(baseline.getWorkflowExecutionId())) {
            pipelineWorkflowExecution.setBaseline(isBaseline);
          }
        }));
    wingsPersistence.merge(workflowExecution);
  }

  private void updatePipeLineIfNecessary(String executionId) {
    WorkflowExecution workflowExecution = wingsPersistence.get(WorkflowExecution.class, executionId);
    if (workflowExecution == null || workflowExecution.getWorkflowType() != WorkflowType.PIPELINE) {
      return;
    }
    boolean markBaseline = false;
    PipelineExecution pipelineExecution = workflowExecution.getPipelineExecution();
    if (pipelineExecution == null) {
      return;
    }

    List<PipelineStageExecution> pipelineStageExecutions = pipelineExecution.getPipelineStageExecutions();
    if (isEmpty(pipelineStageExecutions)) {
      return;
    }

    for (PipelineStageExecution pipelineStageExecution : pipelineStageExecutions) {
      for (WorkflowExecution pipelineWorkflowExecution : pipelineStageExecution.getWorkflowExecutions()) {
        if (pipelineWorkflowExecution.isBaseline()) {
          markBaseline = true;
          break;
        }
      }
      if (markBaseline) {
        break;
      }
    }

    wingsPersistence.updateField(WorkflowExecution.class, executionId, "isBaseline", markBaseline);
  }

  private void updateDataAndAnalysisRecords(String workflowExecutionId, String appId) {
    updateValidUntillTtl(workflowExecutionId, appId, NewRelicMetricDataRecord.class);
    updateTtl(workflowExecutionId, appId, NewRelicMetricAnalysisRecord.class);
    updateTtl(workflowExecutionId, appId, TimeSeriesMLAnalysisRecord.class);
    updateTtl(workflowExecutionId, appId, LogMLAnalysisRecord.class);
    updateTtlForDataRecords(workflowExecutionId);
  }

  private <T extends Base> void updateTtl(String workflowExecutionId, String appId, Class<T> recordClass) {
    wingsPersistence.update(wingsPersistence.createQuery(recordClass)
                                .filter("workflowExecutionId", workflowExecutionId)
                                .filter(WorkflowExecutionKeys.appId, appId),
        wingsPersistence.createUpdateOperations(recordClass).set("validUntil", BASELINE_TTL));
  }

  private <T extends PersistentEntity & ValidUntilAccess> void updateValidUntillTtl(
      String workflowExecutionId, String appId, Class<T> recordClass) {
    wingsPersistence.update(wingsPersistence.createQuery(recordClass)
                                .filter("workflowExecutionId", workflowExecutionId)
                                .filter(WorkflowExecutionKeys.appId, appId),
        wingsPersistence.createUpdateOperations(recordClass).set(ValidUntilAccess.VALID_UNTIL_KEY, BASELINE_TTL));
  }

  private void updateTtlForDataRecords(String workflowExecutionId) {
    PageRequest<TimeSeriesDataRecord> pageRequest =
        PageRequestBuilder.aPageRequest()
            .withLimit(UNLIMITED)
            .addFilter(TimeSeriesMetricRecordKeys.workflowExecutionId, SearchFilter.Operator.EQ, workflowExecutionId)
            .build();
    List<TimeSeriesDataRecord> objects = dataStoreService.list(TimeSeriesDataRecord.class, pageRequest);
    if (isNotEmpty(objects)) {
      objects.forEach(object -> object.setValidUntil(BASELINE_TTL));
      dataStoreService.save(TimeSeriesDataRecord.class, objects, false);
    }
  }

  @Override
  public String getBaselineExecutionId(String appId, String workflowId, String envId, String serviceId) {
    WorkflowExecutionBaseline executionBaseline = wingsPersistence.createQuery(WorkflowExecutionBaseline.class)
                                                      .filter(WorkflowExecutionBaselineKeys.appId, appId)
                                                      .filter(WorkflowExecutionBaseline.WORKFLOW_ID_KEY, workflowId)
                                                      .filter(WorkflowExecutionBaseline.ENV_ID_KEY, envId)
                                                      .filter(WorkflowExecutionBaseline.SERVICE_ID_KEY, serviceId)
                                                      .get();
    return executionBaseline == null ? null : executionBaseline.getWorkflowExecutionId();
  }
}
