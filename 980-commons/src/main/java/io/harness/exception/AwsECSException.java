package io.harness.exception;

import static io.harness.eraro.ErrorCode.AWS_ECS_ERROR;

import io.harness.eraro.Level;

public class AwsECSException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public AwsECSException(String message) {
    this(message, null);
  }

  public AwsECSException(String message, Throwable th) {
    super(message, th, AWS_ECS_ERROR, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
