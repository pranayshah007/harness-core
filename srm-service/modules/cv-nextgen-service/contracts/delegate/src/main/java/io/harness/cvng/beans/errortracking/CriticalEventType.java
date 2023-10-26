/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.beans.errortracking;

public enum CriticalEventType {
  ANY("Any"),
  CAUGHT_EXCEPTION("Caught Exceptions"),
  UNCAUGHT_EXCEPTION("Uncaught Exceptions"),
  SWALLOWED_EXCEPTION("Swallowed Exceptions"),
  LOGGED_ERROR("Logged Errors"),
  LOGGED_WARNING("Logged Warnings"),
  HTTP_ERROR("Http Errors"),
  CUSTOM_ERROR("Custom Errors");

  private final String displayName;

  CriticalEventType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return this.displayName;
  }
}
