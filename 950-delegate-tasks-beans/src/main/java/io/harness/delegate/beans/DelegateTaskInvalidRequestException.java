package io.harness.delegate.beans;

import static io.harness.eraro.ErrorCode.INVALID_REQUEST;

import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;

public class DelegateTaskInvalidRequestException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public DelegateTaskInvalidRequestException(String taskId) {
    super(taskId, null, INVALID_REQUEST, Level.ERROR, null, EnumSet.of(FailureType.EXPIRED));
    param(MESSAGE_KEY, taskId);
  }
}
