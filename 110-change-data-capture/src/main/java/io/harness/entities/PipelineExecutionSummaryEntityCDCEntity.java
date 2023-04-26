/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changehandlers.PlanExecutionSummaryCIStageChangeDataHandler;
import io.harness.changehandlers.PlanExecutionSummaryCdChangeDataHandler;
import io.harness.changehandlers.PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew;
import io.harness.changehandlers.PlanExecutionSummaryChangeDataHandler;
import io.harness.changehandlers.PlanExecutionSummaryChangeDataHandlerAllStages;
import io.harness.changehandlers.RuntimeInputsInfoCDChangeDataHandler;
import io.harness.changehandlers.TagsInfoNGCDChangeDataHandler;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class PipelineExecutionSummaryEntityCDCEntity implements CDCEntity<PipelineExecutionSummaryEntity> {
  @Inject private PlanExecutionSummaryChangeDataHandler planExecutionSummaryChangeDataHandler;
  @Inject private PlanExecutionSummaryCdChangeDataHandler planExecutionSummaryCdChangeDataHandler;
  @Inject private PlanExecutionSummaryCIStageChangeDataHandler planExecutionSummaryCIStageChangeDataHandler;
  @Inject private PlanExecutionSummaryChangeDataHandlerAllStages planExecutionSummaryChangeDataHandlerAllStages;
  @Inject private TagsInfoNGCDChangeDataHandler tagsInfoNGCDChangeDataHandler;
  @Inject private RuntimeInputsInfoCDChangeDataHandler runtimeInputsInfoCDChangeDataHandler;
  @Inject
  private PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew
      planExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("PipelineExecutionSummaryEntityAllStages")) {
      return planExecutionSummaryChangeDataHandlerAllStages;
    } else if (handlerClass.contentEquals("PipelineExecutionSummaryEntity")) {
      return planExecutionSummaryChangeDataHandler;
    } else if (handlerClass.contentEquals("PipelineExecutionSummaryEntityCD")) {
      return planExecutionSummaryCdChangeDataHandler;
    } else if (handlerClass.contentEquals("PipelineExecutionSummaryEntityServiceAndInfra")) {
      return planExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew;
    } else if (handlerClass.contentEquals("PipelineExecutionSummaryEntityCIStage")) {
      return planExecutionSummaryCIStageChangeDataHandler;
    } else if (handlerClass.contentEquals("TagsInfoNGCD")) {
      return tagsInfoNGCDChangeDataHandler;
    } else if (handlerClass.contentEquals("RuntimeInputsInfo")) {
      return runtimeInputsInfoCDChangeDataHandler;
    }
    return null;
  }

  @Override
  public Class<PipelineExecutionSummaryEntity> getSubscriptionEntity() {
    return PipelineExecutionSummaryEntity.class;
  }
}
