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
import io.harness.beans.FeatureName;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import io.harness.changehandlers.PlanExecutionSummaryCdChangeDataHandler;
import io.harness.changehandlers.PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew;
import io.harness.changehandlers.PlanExecutionSummaryChangeDataHandler;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class PipelineExecutionSummaryEntityCDCEntity implements CDCEntity<PipelineExecutionSummaryEntity> {
  @Inject CfClient cfClient;
  @Inject private PlanExecutionSummaryChangeDataHandler planExecutionSummaryChangeDataHandler;
  @Inject private PlanExecutionSummaryCdChangeDataHandler planExecutionSummaryCdChangeDataHandler;
  @Inject
  private PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew
      planExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew;
  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    boolean debeziumEnabled =
        cfClient.boolVariation(FeatureName.DEBEZIUM_ENABLED.toString(), Target.builder().build(), false);
    if (handlerClass.contentEquals("PipelineExecutionSummaryEntity")) {
      return planExecutionSummaryChangeDataHandler;
    } else if (handlerClass.contentEquals("PipelineExecutionSummaryEntityCD")) {
      if (!debeziumEnabled) {
        return planExecutionSummaryCdChangeDataHandler;
      } else {
        log.info("FF {} is true.", FeatureName.DEBEZIUM_ENABLED.toString());
        return null;
      }
    } else if (handlerClass.contentEquals("PipelineExecutionSummaryEntityServiceAndInfra")) {
      return planExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew;
    }
    return null;
  }

  @Override
  public Class<? extends PersistentEntity> getSubscriptionEntity() {
    return PipelineExecutionSummaryEntity.class;
  }
}
