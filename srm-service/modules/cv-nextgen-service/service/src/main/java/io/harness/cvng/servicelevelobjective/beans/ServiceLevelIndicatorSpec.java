/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.servicelevelobjective.beans.slispec.MetricLessServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slispec.RequestBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slispec.WindowBasedServiceLevelIndicatorSpec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = WindowBasedServiceLevelIndicatorSpec.class, name = "Window")
  , @JsonSubTypes.Type(value = RequestBasedServiceLevelIndicatorSpec.class, name = "Request"),
      @JsonSubTypes.Type(value = MetricLessServiceLevelIndicatorSpec.class, name = "MetricLess"),
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
public abstract class ServiceLevelIndicatorSpec {
  @JsonIgnore public abstract SLIEvaluationType getType();

  @JsonIgnore
  public abstract void generateNameAndIdentifier(
      String serviceLevelObjectiveIdentifier, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO);
}
