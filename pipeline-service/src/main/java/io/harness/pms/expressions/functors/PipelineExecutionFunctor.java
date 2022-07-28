/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(PIPELINE)
public class PipelineExecutionFunctor implements LateBindingValue {
  private final PMSExecutionService pmsExecutionService;
  PipelineExpressionHelper pipelineExpressionHelper;
  private final Ambiance ambiance;

  public PipelineExecutionFunctor(
      PMSExecutionService pmsExecutionService, PipelineExpressionHelper pipelineExpressionHelper, Ambiance ambiance) {
    this.pmsExecutionService = pmsExecutionService;
    this.pipelineExpressionHelper = pipelineExpressionHelper;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
            ambiance.getPlanExecutionId());
    Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("triggerType", pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggerType().toString());
    Map<String, String> triggeredByMap = new HashMap<>();
    triggeredByMap.put(
        "name", pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggeredBy().getIdentifier());
    triggeredByMap.put("email",
        pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email"));
    jsonObject.put("triggeredBy", triggeredByMap);
    addExecutionUrlMap(jsonObject);
    return jsonObject;
  }

  private void addExecutionUrlMap(Map<String, Object> jsonObject) {
    Map<String, String> executionMap = new HashMap<>();
    executionMap.put("url", pipelineExpressionHelper.generateUrl(ambiance));
    jsonObject.put("execution", executionMap);
  }
}
