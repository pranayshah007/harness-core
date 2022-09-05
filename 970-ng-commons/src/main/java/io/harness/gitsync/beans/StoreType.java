/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.beans;

public enum StoreType {
  // Will be used when entity is not persisted on git but rather live in DATABASE
  INLINE,

  // Will be used when entity is in git repo
  REMOTE;

  public static StoreType getFromStringOrNull(String value) {
    try {
      return StoreType.valueOf(value);
    } catch (Exception e) {
      return null;
    }
  }
}
