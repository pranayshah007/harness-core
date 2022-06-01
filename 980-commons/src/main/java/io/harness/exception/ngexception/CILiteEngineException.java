/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception;

public class CILiteEngineException extends RuntimeException {
  private static final String MESSAGE_KEY = "message";

  public CILiteEngineException(Exception e) {
    super(e);
  }

  public CILiteEngineException(String message) {
    super(message);
  }

  public CILiteEngineException(String message, Exception e) {
    super(message, e);
  }
}
