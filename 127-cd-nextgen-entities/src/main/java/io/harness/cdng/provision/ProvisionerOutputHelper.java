/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.executions.steps.ExecutionNodeType.INFRASTRUCTURE_PROVISIONER_STEP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.OutputExpressionConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class ProvisionerOutputHelper {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public void saveProvisionerOutputByStepIdentifier(Ambiance ambiance, ExecutionSweepingOutput output) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    if (!currentLevel.hasStepType()) {
      throw new InvalidRequestException(
          format("Found invalid node type while saving provisioner outputs, required step type, found type: %s",
              currentLevel.getNodeType()));
    }

    String provisionerStepIdentifier = currentLevel.getIdentifier();
    if (isEmpty(provisionerStepIdentifier)) {
      throw new InvalidRequestException(
          format("Not found provisioner step identifier, provisioner class: %s", output.getClass()));
    }

    if (!AmbianceUtils.isCurrentLevelChildOfStep(ambiance, INFRASTRUCTURE_PROVISIONER_STEP.getName())) {
      log.info("Not saving provisioner outputs, planExecutionId: {}, stageExecutionId: {}, name: {}",
          ambiance.getPlanExecutionId(), ambiance.getStageExecutionId(), provisionerStepIdentifier);
      return;
    }

    executionSweepingOutputService.consume(
        ambiance, getProvisionerOutputRefObjectName(provisionerStepIdentifier), output, StepCategory.STAGE.name());
  }

  public Map<String, Object> getProvisionerOutputAsMap(Ambiance ambiance, String provisionerIdentifier) {
    Optional<Map<String, Object>> provisionerOutput = executionSweepingOutputService.resolveFromJsonAsMap(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(getProvisionerOutputRefObjectName(provisionerIdentifier)));

    if (provisionerOutput.isEmpty()) {
      throw new InvalidRequestException(
          format("Not found provisioner output, provisionerIdentifier: %s", provisionerIdentifier));
    }

    return provisionerOutput.get();
  }

  private String getProvisionerOutputRefObjectName(final String provisionerIdentifier) {
    return format("%s_%s", OutputExpressionConstants.PROVISIONER, provisionerIdentifier);
  }
}
