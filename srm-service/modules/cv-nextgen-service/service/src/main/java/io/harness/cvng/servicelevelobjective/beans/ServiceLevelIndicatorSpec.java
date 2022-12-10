/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.cvng.CVConstants.SLI_METRIC_TYPE;

import io.harness.cvng.servicelevelobjective.beans.slimetricspec.SLIMetricSpec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceLevelIndicatorSpec {
  @JsonProperty(SLI_METRIC_TYPE) SLIMetricType type;
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = SLI_METRIC_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true)
  @Valid
  @NotNull
  SLIMetricSpec spec;

  public SLIMetricType getType() {
    return spec.getType();
  }
}
