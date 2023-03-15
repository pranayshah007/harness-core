/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.verification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.beans.AutoVerificationJobSpec;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.core.beans.CVVerifyStepNode;
import io.harness.cvng.core.beans.StepSpecTypeConstants;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.AbstractLogAnalysisState;
import software.wings.sm.states.AbstractMetricAnalysisState;

@OwnedBy(HarnessTeam.CDC)
public abstract class VerificationBaseService extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.VERIFY;
  }

  protected AbstractStepNode getVerifySpec(GraphNode graphNode, CaseFormat identifierCaseFormat) {
    State baseState = getState(graphNode);
    ParameterField<String> sensitivity = MigratorUtility.RUNTIME_INPUT;
    ParameterField<String> duration = MigratorUtility.RUNTIME_INPUT;

    if (baseState instanceof AbstractLogAnalysisState) {
      AbstractLogAnalysisState state = (AbstractLogAnalysisState) baseState;
      sensitivity = ParameterField.createValueField(state.getAnalysisTolerance().name());
      duration = ParameterField.createValueField(state.getTimeDuration() + "m");
    }
    if (baseState instanceof AbstractMetricAnalysisState) {
      AbstractMetricAnalysisState state = (AbstractMetricAnalysisState) baseState;
      sensitivity = ParameterField.createValueField(state.getAnalysisTolerance().name());
      duration = ParameterField.createValueField(state.getTimeDuration() + "m");
    }

    CVVerifyStepNode verifyStepNode = new CVVerifyStepNode();
    baseSetup(graphNode, verifyStepNode, identifierCaseFormat);
    verifyStepNode.setVerifyStepInfo(CVNGStepInfo.builder()
                                         .spec(AutoVerificationJobSpec.builder()
                                                   .sensitivity(sensitivity)
                                                   .duration(duration)
                                                   .deploymentTag(MigratorUtility.RUNTIME_INPUT)
                                                   .build())
                                         .type("Auto")
                                         .build());
    return verifyStepNode;
  }
}
