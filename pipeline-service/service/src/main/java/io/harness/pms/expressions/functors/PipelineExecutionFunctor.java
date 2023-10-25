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
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.StoreTypeMapper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.RetryExecutionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class PipelineExecutionFunctor implements LateBindingValue {
  PipelineExpressionHelper pipelineExpressionHelper;

  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final Ambiance ambiance;

  public PipelineExecutionFunctor(PipelineExpressionHelper pipelineExpressionHelper,
      PlanExecutionMetadataService planExecutionMetadataService, PmsGitSyncHelper pmsGitSyncHelper, Ambiance ambiance) {
    this.pipelineExpressionHelper = pipelineExpressionHelper;
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("triggerType", ambiance.getMetadata().getTriggerInfo().getTriggerType().toString());
    Map<String, String> triggeredByMap = new HashMap<>();
    triggeredByMap.put("name", ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier());
    triggeredByMap.put(
        "email", ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email"));
    String triggerIdentifier1 = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getTriggerIdentifier();
    triggeredByMap.put("triggerIdentifier", isNotEmpty(triggerIdentifier1) ? triggerIdentifier1 : null);
    jsonObject.put("triggeredBy", triggeredByMap);

    // Removed run sequence From PipelineStepParameter as run sequence is set just before start of execution and not
    // during plan creation
    jsonObject.put("sequenceId", ambiance.getMetadata().getRunSequence());
    StoreType storeType = StoreTypeMapper.fromPipelineStoreType(ambiance.getMetadata().getPipelineStoreType());
    jsonObject.put("storeType", storeType != null ? storeType : StoreType.INLINE);
    EntityGitDetails entityGitDetails =
        pmsGitSyncHelper.getEntityGitDetailsFromBytes(ambiance.getMetadata().getGitSyncBranchContext());
    if (entityGitDetails != null) {
      jsonObject.put("branch", entityGitDetails.getBranch());
      jsonObject.put("repo", entityGitDetails.getRepoName());
    }
    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        planExecutionMetadataService.findByPlanExecutionId(ambiance.getPlanExecutionId());
    if (planExecutionMetadataOptional.isPresent()) {
      PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataOptional.get();
      jsonObject.put("resumedExecutionId",
          RetryExecutionUtils.getRootExecutionId(ambiance, planExecutionMetadata.getRetryExecutionInfo()));
      // block to add selected stages identifier
      try {
        // If Selective stage execution is allowed, add from StagesExecutionMetadata
        if (planExecutionMetadata.getAllowStagesExecution() != null
            && planExecutionMetadata.getAllowStagesExecution()) {
          jsonObject.put("selectedStages", planExecutionMetadata.getStagesExecutionMetadata().getStageIdentifiers());
        } else {
          List<YamlField> stageFields = YamlUtils.extractStageFieldsFromPipeline(planExecutionMetadata.getYaml());
          List<String> stageIdentifiers =
              stageFields.stream()
                  .map(stageField -> stageField.getNode().getField("identifier").getNode().asText())
                  .collect(Collectors.toList());

          jsonObject.put("selectedStages", stageIdentifiers);
        }
      } catch (Exception ex) {
        throw new InvalidRequestException("Failed to fetch selected stages");
      }
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
