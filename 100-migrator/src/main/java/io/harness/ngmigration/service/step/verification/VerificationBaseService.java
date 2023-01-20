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
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.states.AbstractMetricAnalysisState;

@OwnedBy(HarnessTeam.CDC)
public abstract class VerificationBaseService implements StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.VERIFY;
  }

  protected AbstractStepNode getVerifySpec(GraphNode graphNode) {
    AbstractMetricAnalysisState state = (AbstractMetricAnalysisState) getState(graphNode);
    CVVerifyStepNode verifyStepNode = new CVVerifyStepNode();
    baseSetup(graphNode, verifyStepNode);
    verifyStepNode.setVerifyStepInfo(
        CVNGStepInfo.builder()
            .spec(AutoVerificationJobSpec.builder()
                      .sensitivity(ParameterField.createValueField(state.getAnalysisTolerance().name()))
                      .duration(ParameterField.createValueField(state.getTimeDuration()))
                      .deploymentTag(MigratorUtility.RUNTIME_INPUT)
                      .build())
            .type("Auto")
            .build());
    return verifyStepNode;
  }
}
