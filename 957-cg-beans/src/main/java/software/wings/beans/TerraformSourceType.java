/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public enum TerraformSourceType {
  S3("Amazon S3"),
  GIT("Git Repository"),
  UNKNOWN("Unknown");

  private final String sourceTypeLabel;

  TerraformSourceType(String sourceTypeLabel) {
    this.sourceTypeLabel = sourceTypeLabel;
  }

  public static String getSourceType(String sourceType) {
    String sourceTypeLabel = UNKNOWN.sourceTypeLabel;
    for (TerraformSourceType type : values()) {
      if (type.name().equalsIgnoreCase(sourceType)) {
        sourceTypeLabel = type.sourceTypeLabel;
        break;
      }
    }
    return sourceTypeLabel;
  }
}
