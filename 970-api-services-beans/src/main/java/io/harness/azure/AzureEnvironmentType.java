/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure;

import lombok.Getter;

public enum AzureEnvironmentType {
  AZURE("AzurePublicCloud"),
  AZURE_US_GOVERNMENT("AzureUSGovernmentCloud");

  @Getter private String displayName;

  AzureEnvironmentType(String displayName) {
    this.displayName = displayName;
  }
}
