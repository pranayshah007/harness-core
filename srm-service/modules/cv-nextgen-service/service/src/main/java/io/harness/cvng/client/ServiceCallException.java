/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.WingsException;

import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceCallException extends WingsException {
  private int responseCode;
  private String errorMessage;
  private String errorBody;
  private ErrorCode errorCode;
  private List<ResponseMessage> responseMessages;

  public ServiceCallException(int responseCode, String message, Throwable cause) {
    super("Response code: " + responseCode + ", Message: " + message, cause);
    this.responseCode = responseCode;
    this.errorMessage = message;
  }

  public ServiceCallException(int responseCode, String message, String errorBody) {
    super("Response code: " + responseCode + ", Message: " + message);
    this.responseCode = responseCode;
    this.errorMessage = message;
    this.errorBody = errorBody;
  }

  public ServiceCallException(Throwable throwable) {
    super("ServiceCallException: " + throwable.getMessage(), throwable);
  }

  public ServiceCallException(
      ErrorCode errorCode, int responseCode, String message, String errorBody, List<ResponseMessage> responseMessages) {
    super(errorCode, "Response code: " + responseCode + ", Message: " + message);
    this.errorCode = errorCode;
    this.responseCode = responseCode;
    this.errorMessage = message;
    this.errorBody = errorBody;
    this.responseMessages = responseMessages;
  }
}
