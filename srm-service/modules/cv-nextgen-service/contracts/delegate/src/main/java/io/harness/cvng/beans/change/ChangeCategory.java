/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum ChangeCategory {
  //@JsonProperty added for swagger as it doesn't understand @JsonValue
  @JsonProperty("Deployment") DEPLOYMENT("Deployment", "Deployment"),
  @JsonProperty("Infrastructure") INFRASTRUCTURE("Infrastructure", "Infrastructure"),
  @JsonProperty("Alert") ALERTS("Alert", "Incident"),
  @JsonProperty("FeatureFlag") FEATURE_FLAG("FeatureFlag", "Feature Flag"),
  @JsonProperty("ChaosExperiment") CHAOS_EXPERIMENT("ChaosExperiment", "Chaos Experiment");

  private String value;
  private final String displayName;

  @JsonValue
  public String getValue() {
    return value;
  }

  public String getDisplayName() {
    return this.displayName;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
