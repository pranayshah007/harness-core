/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class PipelineExecutionFunctor implements LateBindingValue {
  private final PMSExecutionService pmsExecutionService;
  PipelineExpressionHelper pipelineExpressionHelper;

  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final Ambiance ambiance;

  public PipelineExecutionFunctor(PMSExecutionService pmsExecutionService,
      PipelineExpressionHelper pipelineExpressionHelper, PlanExecutionMetadataService planExecutionMetadataService,
      Ambiance ambiance) {
    this.pmsExecutionService = pmsExecutionService;
    this.pipelineExpressionHelper = pipelineExpressionHelper;
    this.planExecutionMetadataService = planExecutionMetadataService;
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
    String triggerName =
        pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggeredBy().getTriggerIdentifier();
    triggeredByMap.put("triggerName", isNotEmpty(triggerName) ? triggerName : null);
    jsonObject.put("triggeredBy", triggeredByMap);

    // Removed run sequence From PipelineStepParameter as run sequence is set just before start of execution and not
    // during plan creation
    jsonObject.put("sequenceId", pipelineExecutionSummaryEntity.getRunSequence());
    jsonObject.put(
        "resumedExecutionId", pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());

    // block to add selected stages identifier
    try {
      // If Selective stage execution is allowed, add from StagesExecutionMetadata
      if (pipelineExecutionSummaryEntity.getAllowStagesExecution() != null
          && pipelineExecutionSummaryEntity.getAllowStagesExecution()) {
        jsonObject.put(
            "selectedStages", pipelineExecutionSummaryEntity.getStagesExecutionMetadata().getStageIdentifiers());
      } else {
        Optional<PlanExecutionMetadata> planExecutionMetadata =
            planExecutionMetadataService.findByPlanExecutionId(ambiance.getPlanExecutionId());

        if (planExecutionMetadata.isPresent()) {
          List<YamlField> stageFields = YamlUtils.extractStageFieldsFromPipeline(planExecutionMetadata.get().getYaml());
          List<String> stageIdentifiers =
              stageFields.stream()
                  .map(stageField -> stageField.getNode().getField("identifier").getNode().asText())
                  .collect(Collectors.toList());

          jsonObject.put("selectedStages", stageIdentifiers);
        }
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Failed to fetch selected stages");
    }
    addExecutionUrlMap(jsonObject);
    return jsonObject;
  }

  private void addExecutionUrlMap(Map<String, Object> jsonObject) {
    Map<String, String> executionMap = new HashMap<>();
    String pipelineExecutionUrl = pipelineExpressionHelper.generateUrl(ambiance);
    executionMap.put("url", pipelineExecutionUrl);
    jsonObject.put("execution", executionMap);
    jsonObject.put(OrchestrationConstants.EXECUTION_URL, pipelineExecutionUrl);
  }
}
