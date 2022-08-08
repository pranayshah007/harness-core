/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;

@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
public class StepExecutionSummary {
  private String stepName;
  private ExecutionStatus status;
  private String errorCode;
  private String message;
  private ContextElement element;

  public ExecutionStatus getStatus() {
    return status;
  }

  public void setStatus(ExecutionStatus status) {
    this.status = status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ContextElement getElement() {
    return element;
  }

  public void setElement(ContextElement element) {
    this.element = element;
  }

  public String getStepName() {
    return stepName;
  }

  public void setStepName(String stepName) {
    this.stepName = stepName;
  }

  public static final class StepExecutionSummaryBuilder {
    private String stepName;
    private ExecutionStatus status;
    private String errorCode;
    private String message;
    private ContextElement element;

    private StepExecutionSummaryBuilder() {}

    public static StepExecutionSummaryBuilder aStepExecutionSummary() {
      return new StepExecutionSummaryBuilder();
    }

    public StepExecutionSummaryBuilder withStepName(String stepName) {
      this.stepName = stepName;
      return this;
    }

    public StepExecutionSummaryBuilder withStatus(ExecutionStatus status) {
      this.status = status;
      return this;
    }

    public StepExecutionSummaryBuilder withErrorCode(String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    public StepExecutionSummaryBuilder withMessage(String message) {
      this.message = message;
      return this;
    }

    public StepExecutionSummaryBuilder withElement(ContextElement element) {
      this.element = element;
      return this;
    }

    public StepExecutionSummary build() {
      StepExecutionSummary stepExecutionSummary = new StepExecutionSummary();
      stepExecutionSummary.setStepName(stepName);
      stepExecutionSummary.setStatus(status);
      stepExecutionSummary.setErrorCode(errorCode);
      stepExecutionSummary.setMessage(message);
      stepExecutionSummary.setElement(element);
      return stepExecutionSummary;
    }
  }
}
