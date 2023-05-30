/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions.step;

import io.harness.ngmigration.beans.ScriptProvisionerOutput;
import io.harness.ngmigration.beans.StepOutput;

public class ShellScriptProvisionerStepFunctor extends StepExpressionFunctor {
  private StepOutput stepOutput;

  public ShellScriptProvisionerStepFunctor(StepOutput stepOutput) {
    super(stepOutput);
    this.stepOutput = stepOutput;
  }

  @Override
  public synchronized Object get(Object key) {
    return ScriptProvisionerOutput.builder()
        .stepOutput(stepOutput)
        .currentStageIdentifier(getCurrentStageIdentifier())
        .key((String) key)
        .build();
  }
}
