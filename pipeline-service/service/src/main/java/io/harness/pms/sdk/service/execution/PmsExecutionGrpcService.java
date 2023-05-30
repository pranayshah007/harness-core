/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.service.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.pms.contracts.service.ExecutionSummaryResponse;
import io.harness.pms.contracts.service.ExecutionSummaryUpdateRequest;
import io.harness.pms.contracts.service.PmsExecutionServiceGrpc.PmsExecutionServiceImplBase;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import java.util.Collection;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PmsExecutionGrpcService extends PmsExecutionServiceImplBase {
  private static final String PIPELINE_MODULE_INFO_UPDATE_KEY = "moduleInfo.%s.%s";
  private static final String STAGE_MODULE_INFO_UPDATE_KEY = "layoutNodeMap.%s.moduleInfo.%s.%s";

  @Inject PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public void updateExecutionSummary(
      ExecutionSummaryUpdateRequest request, StreamObserver<ExecutionSummaryResponse> responseObserver) {
    updatePipelineInfoJson(request);
    updateStageModuleInfo(request);
    responseObserver.onNext(ExecutionSummaryResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  void updatePipelineInfoJson(ExecutionSummaryUpdateRequest request) {
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
}
