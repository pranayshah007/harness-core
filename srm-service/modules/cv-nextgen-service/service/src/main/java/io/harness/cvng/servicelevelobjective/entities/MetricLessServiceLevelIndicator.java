/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.exception.InvalidArgumentsException;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("METRIC_LESS")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "MetricLessServiceLevelIndicatorKeys")
@EqualsAndHashCode(callSuper = true)
public class MetricLessServiceLevelIndicator extends ServiceLevelIndicator {
  @Override
  public SLIMetricType getSLIMetricType() {
    return null;
  }

  @Override
  public SLIEvaluationType getSLIEvaluationType() {
    return SLIEvaluationType.METRIC_LESS;
  }

  @Override
  public List<String> getMetricNames() {
    return null;
  }

  @Override
  public Integer getConsiderConsecutiveMinutes() {
    throw new InvalidArgumentsException("Value doesn't exist for metric less slo");
  }

  @Override
  public Boolean getConsiderAllConsecutiveMinutesFromStartAsBad() {
    throw new InvalidArgumentsException("Value doesn't exist for metric less slo");
  }

  @Override
  public boolean isUpdatable(ServiceLevelIndicator serviceLevelIndicator) {
    return true;
  }

  @Override
  public boolean shouldReAnalysis(ServiceLevelIndicator serviceLevelIndicator) {
    return false;
  }
}
