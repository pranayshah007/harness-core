/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.service.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.pms.contracts.service.ExecutionSummaryResponse;
import io.harness.pms.contracts.service.ExecutionSummaryUpdateRequest;
import io.harness.pms.contracts.service.PmsExecutionServiceGrpc.PmsExecutionServiceImplBase;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.beans.ExecutionSummaryUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo.GraphUpdateInfoKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.executions.GraphUpdateInfoRepository;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PmsExecutionGrpcService extends PmsExecutionServiceImplBase {
  private static final String MODULE_INFO_UPDATE_KEY = "executionSummaryUpdateInfo.moduleInfo.%s.%s";
  private static final String PIPELINE_MODULE_INFO_UPDATE_KEY = "moduleInfo.%s.%s";
  private static final String STAGE_MODULE_INFO_UPDATE_KEY = "layoutNodeMap.%s.moduleInfo.%s.%s";

  @Inject PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject GraphUpdateInfoRepository graphUpdateInfoRepository;
  @Inject OrchestrationLogPublisher orchestrationLogPublisher;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;

  @Override
  public void updateExecutionSummary(
      ExecutionSummaryUpdateRequest request, StreamObserver<ExecutionSummaryResponse> responseObserver) {
    if (pmsFeatureFlagService.isEnabled(
            getAccountId(request.getPlanExecutionId()), FeatureName.CDS_MERGE_PIPELINE_EXECUTION_SUMMARY_UPDATE_FLOW)) {
      updatePipelineInfoJson(request);
      updateStageModuleInfo(request);
    } else {
      updatePipelineInfoJsonInPipelineExecutionSummary(request);
      updateStageModuleInfoInPipelineExecutionSummary(request);
    }
    responseObserver.onNext(ExecutionSummaryResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  void updatePipelineInfoJson(ExecutionSummaryUpdateRequest request) {
    String moduleName = request.getModuleName();
    String planExecutionId = request.getPlanExecutionId();
    Map<String, Object> pipelineInfoDoc = RecastOrchestrationUtils.fromJson(request.getPipelineModuleInfoJson());
    Map<String, LinkedHashMap<String, Object>> moduleInfo = new HashMap<>();
    if (pipelineInfoDoc != null) {
      Optional<GraphUpdateInfo> graphUpdateInfoOptional =
          graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
              planExecutionId, StepCategory.PIPELINE);
      if (graphUpdateInfoOptional.isEmpty()) {
        moduleInfo.put(moduleName, (LinkedHashMap<String, Object>) pipelineInfoDoc);
        graphUpdateInfoRepository.save(GraphUpdateInfo.builder()
                                           .planExecutionId(planExecutionId)
                                           .executionSummaryUpdateInfo(ExecutionSummaryUpdateInfo.builder()
                                                                           .stepCategory(StepCategory.PIPELINE)
                                                                           .moduleInfo(moduleInfo)
                                                                           .build())
                                           .build());
      } else {
        Criteria criteria = Criteria.where(GraphUpdateInfoKeys.planExecutionId)
                                .is(planExecutionId)
                                .and(GraphUpdateInfoKeys.stepCategory)
                                .is(StepCategory.PIPELINE);
        updateGraphUpdateInfo(moduleName, (LinkedHashMap<String, Object>) pipelineInfoDoc, criteria);
      }
      orchestrationLogPublisher.onPipelineInfoUpdate(planExecutionId);
    }
  }

  void updatePipelineInfoJsonInPipelineExecutionSummary(ExecutionSummaryUpdateRequest request) {
    String moduleName = request.getModuleName();
    String planExecutionId = request.getPlanExecutionId();
    Map<String, Object> pipelineInfoDoc = RecastOrchestrationUtils.fromJson(request.getPipelineModuleInfoJson());

    Update update = new Update();

    if (pipelineInfoDoc != null) {
      for (Map.Entry<String, Object> entry : pipelineInfoDoc.entrySet()) {
        String key = String.format(PIPELINE_MODULE_INFO_UPDATE_KEY, moduleName, entry.getKey());
        if (entry.getValue() != null && Collection.class.isAssignableFrom(entry.getValue().getClass())) {
          Collection<Object> values = (Collection<Object>) entry.getValue();
          update.addToSet(key).each(values);
        } else {
          if (entry.getValue() != null) {
            update.set(key, entry.getValue());
          }
        }
      }
    }
    Criteria criteria =
        Criteria.where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);
  }

  @VisibleForTesting
  void updateStageModuleInfo(ExecutionSummaryUpdateRequest request) {
    String stageUuid = request.getNodeUuid();
    String moduleName = request.getModuleName();
    String nodeExecutionId = request.getNodeExecutionId();
    String stageInfo = request.getNodeModuleInfoJson();
    String planExecutionId = request.getPlanExecutionId();
    if (EmptyPredicate.isEmpty(stageUuid)) {
      return;
    }
    Map<String, Object> stageInfoDoc = RecastOrchestrationUtils.fromJson(stageInfo);
    Map<String, LinkedHashMap<String, Object>> moduleInfo = new HashMap<>();
    if (stageInfoDoc != null) {
      Optional<GraphUpdateInfo> graphUpdateInfoOptional =
          graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategoryAndNodeExecutionId(
              planExecutionId, StepCategory.STAGE, request.getNodeExecutionId());
      if (graphUpdateInfoOptional.isEmpty()) {
        moduleInfo.put(moduleName, (LinkedHashMap<String, Object>) stageInfoDoc);
        graphUpdateInfoRepository.save(GraphUpdateInfo.builder()
                                           .planExecutionId(planExecutionId)
                                           .nodeExecutionId(nodeExecutionId)
                                           .executionSummaryUpdateInfo(ExecutionSummaryUpdateInfo.builder()
                                                                           .stageUuid(stageUuid)
                                                                           .stepCategory(StepCategory.STAGE)
                                                                           .moduleInfo(moduleInfo)
                                                                           .build())
                                           .build());
      } else {
        Criteria criteria = Criteria.where(GraphUpdateInfoKeys.planExecutionId)
                                .is(planExecutionId)
                                .and(GraphUpdateInfoKeys.stepCategory)
                                .is(StepCategory.STAGE)
                                .and(GraphUpdateInfoKeys.nodeExecutionId)
                                .is(nodeExecutionId);
        updateGraphUpdateInfo(moduleName, (LinkedHashMap<String, Object>) stageInfoDoc, criteria);
      }
      orchestrationLogPublisher.onStageInfoUpdate(planExecutionId, request.getNodeExecutionId());
    }
  }

  void updateStageModuleInfoInPipelineExecutionSummary(ExecutionSummaryUpdateRequest request) {
    String stageUuid = request.getNodeUuid();
    String moduleName = request.getModuleName();
    String stageInfo = request.getNodeModuleInfoJson();
    String planExecutionId = request.getPlanExecutionId();
    if (EmptyPredicate.isEmpty(stageUuid)) {
      return;
    }
    Map<String, Object> stageInfoDoc = RecastOrchestrationUtils.fromJson(stageInfo);

    Update update = new Update();
    if (stageInfoDoc != null) {
      for (Map.Entry<String, Object> entry : stageInfoDoc.entrySet()) {
        String key = String.format(STAGE_MODULE_INFO_UPDATE_KEY, stageUuid, moduleName, entry.getKey());
        if (entry.getValue() != null && Collection.class.isAssignableFrom(entry.getValue().getClass())) {
          Collection<Object> values = (Collection<Object>) entry.getValue();
          update.addToSet(key).each(values);
        } else {
          if (entry.getValue() != null) {
            update.set(key, entry.getValue());
          }
        }
      }
    }
    Criteria criteria =
        Criteria.where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);
  }

  private void updateGraphUpdateInfo(String moduleName, LinkedHashMap<String, Object> infoDoc, Criteria criteria) {
    Update update = new Update();
    for (Map.Entry<String, Object> entry : infoDoc.entrySet()) {
      String key = String.format(MODULE_INFO_UPDATE_KEY, moduleName, entry.getKey());
      if (entry.getValue() != null && Collection.class.isAssignableFrom(entry.getValue().getClass())) {
        Collection<Object> values = (Collection<Object>) entry.getValue();
        update.addToSet(key).each(values);
      } else {
        if (entry.getValue() != null) {
          update.set(key, entry.getValue());
        }
      }
    }
    Query query = new Query(criteria);
    graphUpdateInfoRepository.update(query, update);
  }

  private String getAccountId(String planExecutionId) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    PipelineExecutionSummaryEntity summaryEntity =
        pmsExecutionSummaryRepository.getPipelineExecutionSummaryWithProjections(
            criteria, Set.of(PlanExecutionSummaryKeys.accountId));
    return summaryEntity != null ? summaryEntity.getAccountId() : "";
  }
}
