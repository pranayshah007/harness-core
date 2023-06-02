/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions.step;

import io.harness.ngmigration.beans.StepOutput;

import org.apache.commons.lang3.StringUtils;

public class ServiceNowFunctor extends StepExpressionFunctor {
  private StepOutput stepOutput;
  public ServiceNowFunctor(StepOutput stepOutput) {
    super(stepOutput);
    this.stepOutput = stepOutput;
  }

  @Override
  public synchronized Object get(Object key) {
    String newKey = key.toString();
    if ("issueNumber".equals(key.toString())) {
      newKey = "ticketNumber";
    }
    if (StringUtils.equals(stepOutput.getStageIdentifier(), getCurrentStageIdentifier())) {
      return String.format("<+execution.steps.%s.steps.%s.ticket.%s>", stepOutput.getStepGroupIdentifier(),
          stepOutput.getStepIdentifier(), newKey);
    }

    return String.format("<+pipeline.stages.%s.spec.execution.steps.%s.steps.%s.ticket.%s>",
        stepOutput.getStageIdentifier(), stepOutput.getStepGroupIdentifier(), stepOutput.getStepIdentifier(), newKey);
  }
}
