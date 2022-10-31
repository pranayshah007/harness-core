package io.harness.cdng.chaos;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;

public class ChaosRerunException extends WingsException {
  protected ChaosRerunException(String message) {
    super(message, null, ErrorCode.INVALID_REQUEST, Level.ERROR, USER, EnumSet.of(FailureType.APPLICATION_ERROR));
  }
}
