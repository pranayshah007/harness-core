/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.handlers;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ResolvedInputSetYamlUpdateEventHandler implements OrchestrationEventHandler {
  private static final String STEP_TYPE_TO_UPDATE = OrchestrationStepTypes.PIPELINE_SECTION;

  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private YamlExpressionResolveHelper yamlExpressionResolveHelper;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    if (STEP_TYPE_TO_UPDATE.equals(
            Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(ambiance)).getStepType().getType())) {
      String planExecutionId = ambiance.getPlanExecutionId();
      try {
        PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.getWithFieldsIncludedFromSecondary(
            planExecutionId, Sets.newHashSet(PlanExecutionMetadataKeys.inputSetYaml));
        String resolvedInputSetYaml = resolveExpressionsInYaml(planExecutionMetadata.getInputSetYaml(), ambiance);
        pmsExecutionSummaryService.updateResolvedUserInputSetYaml(planExecutionId, resolvedInputSetYaml);
      } catch (Exception ex) {
        log.error(
            String.format("Error updating resolvedUserInputSetYaml in execution summary for planExecutionId: [%s]",
                planExecutionId),
            ex);
        throw ex;
      }
    }
  }

  private String resolveExpressionsInYaml(String inputSetYaml, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(inputSetYaml)) {
      inputSetYaml = yamlExpressionResolveHelper.resolveExpressionsInYaml(
          inputSetYaml, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, ambiance);
    }
    return inputSetYaml;
  }
}