/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.metricThresholdSpec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = IgnoreMetricThresholdSpec.class, name = "IgnoreThreshold")
  , @JsonSubTypes.Type(value = FailMetricThresholdSpec.class, name = "FailImmediately")
})
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class MetricThresholdSpec {
  @NotNull MetricCustomThresholdActions action;
  public MetricCustomThresholdActions getAction() {
    return action;
  }
}
