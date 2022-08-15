package io.harness.delegate;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class NoGlobalDelegateAccountException extends WingsException {
  public NoGlobalDelegateAccountException(String message, ErrorCode errorCode) {
    super(message, null, errorCode, Level.ERROR, USER, null);
  }
}
