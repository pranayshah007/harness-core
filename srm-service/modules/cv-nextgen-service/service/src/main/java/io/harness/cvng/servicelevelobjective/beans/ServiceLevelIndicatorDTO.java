/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.servicelevelobjective.beans.slispec.WindowBasedServiceLevelIndicatorSpec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceLevelIndicatorDTO {
  String name;
  String identifier;
  SLIEvaluationType type;
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true, defaultImpl = WindowBasedServiceLevelIndicatorSpec.class)
  @Valid
  @NotNull
  ServiceLevelIndicatorSpec spec;
  @Deprecated String healthSourceRef;

  @JsonIgnore
  public String getEvaluationAndMetricType() {
    if (this.getType().equals(SLIEvaluationType.REQUEST)) {
      return type.name();
    } else {
      SLIMetricType sliMetricType = ((WindowBasedServiceLevelIndicatorSpec) this.getSpec()).getSpec().getType();
      return type.name() + "_" + sliMetricType.name();
    }
  }
}
