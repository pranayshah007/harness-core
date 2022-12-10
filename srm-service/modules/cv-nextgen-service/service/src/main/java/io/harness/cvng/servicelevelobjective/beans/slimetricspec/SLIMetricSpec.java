/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slimetricspec;

import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ThresholdSLIMetricSpec.class, name = "Threshold")
  , @JsonSubTypes.Type(value = RatioSLIMetricSpec.class, name = "Ratio"),
})
public abstract class SLIMetricSpec {
  @JsonIgnore public abstract SLIMetricType getType();
  @JsonIgnore public abstract String getMetricName();
}
