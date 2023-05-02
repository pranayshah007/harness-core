/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions.step;

import io.harness.ngmigration.beans.StepOutput;

import org.apache.commons.lang3.StringUtils;

public class JenkinsStepFunctor extends StepExpressionFunctor {
  private StepOutput stepOutput;
  public JenkinsStepFunctor(StepOutput stepOutput) {
    super(stepOutput);
    this.stepOutput = stepOutput;
  }

  @Override
  public synchronized Object get(Object key) {
    if (StringUtils.equals(stepOutput.getStageIdentifier(), getCurrentStageIdentifier())) {
      return String.format("<+execution.steps.%s.steps.%s.build.%s>", stepOutput.getStepGroupIdentifier(),
          stepOutput.getStepIdentifier(), key);
    }

    return String.format("<+pipeline.stages.%s.spec.execution.steps.%s.steps.%s.build.%s>",
        stepOutput.getStageIdentifier(), stepOutput.getStepGroupIdentifier(), stepOutput.getStepIdentifier(), key);
  }
}
