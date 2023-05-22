/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.ssca;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.AbstractStepExecutable;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.client.beans.enforcement.SscaEnforcementSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
public class SscaEnforcementStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = SscaConstants.SSCA_ENFORCEMENT_STEP_TYPE;

  @Inject SSCAServiceUtils sscaServiceUtils;

  @Override
  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    SscaEnforcementSummary enforcementSummary = sscaServiceUtils.getEnforcementSummary(stepExecutionId);

    ObjectMapper oMapper = new ObjectMapper();
    Map<String, String> enforcementSummaryMap =
        oMapper.convertValue(enforcementSummary, new TypeReference<LinkedHashMap<String, String>>() {});
    stepStatus.setOutput(StepMapOutput.builder().map(enforcementSummaryMap).build());
  }
}
