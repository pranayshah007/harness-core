/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.exception.WingsException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceCallException extends WingsException {
  private int responseCode;
  private String errorMessage;
  private String errorBody;

  public ServiceCallException(int responseCode, String message, Throwable cause) {
    super("Response code: " + responseCode + ", Message: " + message, cause);
    this.responseCode = responseCode;
    this.errorMessage = message;
  }

  public ServiceCallException(int responseCode, String message, String errorBody) {
    super("Response code: " + responseCode + ", Message: " + message + ", Error: " + errorBody);
    this.responseCode = responseCode;
    this.errorMessage = message;
    this.errorBody = errorBody;
  }

  public ServiceCallException(Throwable throwable) {
    super("ServiceCallException: " + throwable.getMessage(), throwable);
  }
}
