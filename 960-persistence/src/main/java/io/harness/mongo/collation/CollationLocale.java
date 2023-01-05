/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.collation;

public enum CollationLocale {
  ENGLISH("en");

  private final String code;

  CollationLocale(String code) {
    this.code = code;
  }

  public String getCode() {
    return this.code;
  }

  @Override
  public String toString() {
    return code;
  }
}
