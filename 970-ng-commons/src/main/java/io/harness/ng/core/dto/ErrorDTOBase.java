package io.harness.ng.core.dto;

import io.harness.eraro.ErrorCode;

public interface ErrorDTOBase {
  String getMessage();
  ErrorCode getCode();
}
