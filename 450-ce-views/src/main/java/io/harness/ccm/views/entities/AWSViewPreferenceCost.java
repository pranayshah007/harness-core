/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Show AWS Cost As")
@OwnedBy(CE)
public enum AWSViewPreferenceCost {
  AMORTISED("Amortised"),
  NET_AMORTISED("Net-amortised"),
  BLENDED("Blended"),
  UNBLENDED("Unblended"),
  EFFECTIVE("Effective");

  private final String cost;

  AWSViewPreferenceCost(final String cost) {
    this.cost = cost;
  }

  public String getCost() {
    return cost;
  }

  public static AWSViewPreferenceCost fromString(final String cost) {
    for (final AWSViewPreferenceCost awsViewPreferenceCost : AWSViewPreferenceCost.values()) {
      if (awsViewPreferenceCost.getCost().equals(cost)) {
        return awsViewPreferenceCost;
      }
    }
    throw new IllegalArgumentException(String.format("No AWSViewPreferenceCost constant with text %s found", cost));
  }
}
