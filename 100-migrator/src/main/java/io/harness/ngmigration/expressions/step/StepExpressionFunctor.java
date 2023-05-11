/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions.step;

import io.harness.expression.LateBindingMap;
import io.harness.ngmigration.beans.StepOutput;

public abstract class StepExpressionFunctor extends LateBindingMap {
  private String currentStageIdentifier;
  private StepOutput stepOutput;

  protected StepExpressionFunctor(StepOutput stepOutput) {
    this.stepOutput = stepOutput;
    this.currentStageIdentifier = null;
  }

  public String getCurrentStageIdentifier() {
    return currentStageIdentifier;
  }

  public void setCurrentStageIdentifier(String currentStageIdentifier) {
    this.currentStageIdentifier = currentStageIdentifier;
  }

  public String getCgExpression() {
    return stepOutput.getExpression();
  }

  public StepOutput getStepOutput() {
    return stepOutput;
  }
}
