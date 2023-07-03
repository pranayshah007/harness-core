/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping.exceptions;

/**
 * HandlersException provides a place to define standard exception format for processing variant runner exceptions
 */
public class HandlersException extends RuntimeException {
  public HandlersException(String message, Throwable e) {
    super(message, e);
  }

  public HandlersException(String message) {
    super(message);
  }
}
