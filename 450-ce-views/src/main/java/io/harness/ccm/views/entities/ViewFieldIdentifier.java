/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Perspective filter Category, CLUSTER means Kubernetes")
public enum ViewFieldIdentifier {
  CLUSTER("Cluster"),
  AWS("AWS"),
  GCP("GCP"),
  AZURE("Azure"),
  COMMON("Common"),
  CUSTOM("Custom"),
  BUSINESS_MAPPING("Cost Categories"),
  LABEL("Label");

  public String getDisplayName() {
    return displayName;
  }

  private final String displayName;

  ViewFieldIdentifier(String displayName) {
    this.displayName = displayName;
  }
}
