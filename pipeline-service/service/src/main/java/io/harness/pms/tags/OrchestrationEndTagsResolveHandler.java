/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.tags;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class OrchestrationEndTagsResolveHandler implements OrchestrationEndObserver, AsyncInformObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject PmsEngineExpressionService pmsEngineExpressionService;

  @Override
  public void onEnd(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    Optional<PipelineExecutionSummaryEntity> optional = pmsExecutionSummaryService.getPipelineExecutionSummary(
        accountId, orgId, projectId, ambiance.getPlanExecutionId());
    if (optional.isPresent()) {
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = optional.get();
      List<NGTag> resolvedTags =
          (List<NGTag>) pmsEngineExpressionService.resolve(ambiance, pipelineExecutionSummaryEntity.getTags(), true);
      Update update = new Update().set(PlanExecutionSummaryKeys.tags, resolvedTags);
      pmsExecutionSummaryService.update(ambiance.getPlanExecutionId(), update);
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
