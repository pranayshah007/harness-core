/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.collation;

public enum CollationStrength {
  PRIMARY(1),
  SECONDARY(2),
  TERTIARY(3),
  QUATERNARY(4),
  IDENTICAL(5);

  private final int code;

  public int getCode() {
    return this.code;
  }

  CollationStrength(int code) {
    this.code = code;
  }
}
