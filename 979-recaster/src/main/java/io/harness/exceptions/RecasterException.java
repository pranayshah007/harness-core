/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exceptions;

public class RecasterException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  // fieldPath is to store the path of the field for which classCastException was caught and messageWithoutFieldPath
  // contains %s, which can be replaced with fieldPath while building the exception
  String fieldPath; // Eg:- strategyConfig.repeat.items

  String messageWithoutFieldPath; // Eg:- Exception while resolving the field [%s]

  public RecasterException(final String message) {
    super(message);
  }

  public RecasterException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public RecasterException(
      final String message, final Throwable cause, String fieldPath, String messageWithoutFieldPath) {
    super(message, cause);
    this.fieldPath = fieldPath;
    this.messageWithoutFieldPath = messageWithoutFieldPath;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getMessageWithoutFieldPath() {
    return messageWithoutFieldPath;
  }
}
