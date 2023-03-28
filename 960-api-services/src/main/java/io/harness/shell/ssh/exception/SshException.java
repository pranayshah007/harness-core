package io.harness.shell.ssh.exception;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;

public class SshException extends WingsException {
  public SshException(String message) {
    super(message, null, ErrorCode.INVALID_REQUEST, Level.ERROR, USER, EnumSet.of(FailureType.APPLICATION_ERROR));
  }

  public SshException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

  public SshException(ErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public SshException(ErrorCode errorCode) {
    super(errorCode);
  }
}
