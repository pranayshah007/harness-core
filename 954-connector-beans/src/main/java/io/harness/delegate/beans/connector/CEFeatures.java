/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import io.swagger.annotations.ApiModel;
import lombok.Getter;

@ApiModel("CEFeatures")
public enum CEFeatures {
  BILLING("Cost Management And Billing Export"),
  OPTIMIZATION("Lightwing Cost Optimization"),
  VISIBILITY("Receive Events For Cloud Accounts"),
  GOVERNANCE("Cloud asset governance"),
  COMMITMENT_ORCHESTRATOR("Commitment Orchestrator"),
  CLUSTER_ORCHESTRATOR("Cluster Orchestrator");

  @Getter private final String description;
  CEFeatures(String description) {
    this.description = description;
  }
}
