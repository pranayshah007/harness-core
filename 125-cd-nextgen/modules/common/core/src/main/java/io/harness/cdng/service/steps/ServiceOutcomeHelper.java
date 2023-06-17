/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDC)
public class ServiceOutcomeHelper {
  public ServiceSectionStepOutcome createSectionOutcome(
      Ambiance ambiance, OutcomeService outcomeService, ExecutionSweepingOutputService executionSweepingOutputService) {
    return ServiceSectionStepOutcome.builder()
        .serviceResult(getServiceOutcome(ambiance, outcomeService))
        .variablesResult(getVariablesSweepingOutput(
            ambiance, executionSweepingOutputService, YAMLFieldNameConstants.SERVICE_VARIABLES))
        .artifactResults(getArtifactsOutcome(ambiance, outcomeService))
        .manifestResults(getManifestsOutcome(ambiance, outcomeService))
        .configFileResults(getConfigFilesOutcome(ambiance, outcomeService))
        .build();
  }

  public ServiceConfigStepOutcome createOutcome(
      Ambiance ambiance, OutcomeService outcomeService, ExecutionSweepingOutputService executionSweepingOutputService) {
    return ServiceConfigStepOutcome.builder()
        .serviceResult(getServiceOutcome(ambiance, outcomeService))
        .variablesResult(getVariablesSweepingOutput(
            ambiance, executionSweepingOutputService, YAMLFieldNameConstants.SERVICE_VARIABLES))
        .artifactResults(getArtifactsOutcome(ambiance, outcomeService))
        .manifestResults(getManifestsOutcome(ambiance, outcomeService))
        .configFileResults(getConfigFilesOutcome(ambiance, outcomeService))
        .build();
  }

  public VariablesSweepingOutput getVariablesSweepingOutput(
      Ambiance ambiance, ExecutionSweepingOutputService executionSweepingOutputService, String variableField) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(variableField));
    if (!optionalSweepingOutput.isFound()) {
      return null;
    }
    return (VariablesSweepingOutput) optionalSweepingOutput.getOutput();
  }

  private ServiceStepOutcome getServiceOutcome(Ambiance ambiance, OutcomeService outcomeService) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ServiceStepOutcome) optionalOutcome.getOutcome();
  }

  private ArtifactsOutcome getArtifactsOutcome(Ambiance ambiance, OutcomeService outcomeService) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ArtifactsOutcome) optionalOutcome.getOutcome();
  }

  private ManifestsOutcome getManifestsOutcome(Ambiance ambiance, OutcomeService outcomeService) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ManifestsOutcome) optionalOutcome.getOutcome();
  }

  private ConfigFilesOutcome getConfigFilesOutcome(Ambiance ambiance, OutcomeService outcomeService) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.CONFIG_FILES));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ConfigFilesOutcome) optionalOutcome.getOutcome();
  }
}
