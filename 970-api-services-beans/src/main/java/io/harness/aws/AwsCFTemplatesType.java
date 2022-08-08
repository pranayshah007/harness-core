/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public enum AwsCFTemplatesType {
  BODY("body"),
  S3("s3"),
  GIT("git"),
  UNKNOWN("Unknown");

  private final String value;

  AwsCFTemplatesType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static AwsCFTemplatesType fromValue(String value) {
    for (AwsCFTemplatesType type : values()) {
      if (type.getValue().equals(value)) {
        return type;
      }
    }
    return UNKNOWN;
  }
}
