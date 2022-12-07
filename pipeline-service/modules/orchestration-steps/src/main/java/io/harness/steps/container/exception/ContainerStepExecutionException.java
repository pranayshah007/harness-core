package io.harness.steps.container.exception;

import static io.harness.eraro.ErrorCode.NG_PIPELINE_EXECUTION_EXCEPTION;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class ContainerStepExecutionException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public ContainerStepExecutionException(String message) {
    super(message, null, NG_PIPELINE_EXECUTION_EXCEPTION, Level.ERROR, null, null);
    param(MESSAGE_KEY, message);
  }

  public ContainerStepExecutionException(String message, Throwable cause) {
    super(message, cause, NG_PIPELINE_EXECUTION_EXCEPTION, Level.ERROR, null, null);
  }
}
