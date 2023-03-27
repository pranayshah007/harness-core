/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("THRESHOLD")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "ThresholdServiceLevelIndicatorKeys")
@EqualsAndHashCode(callSuper = true)
public class ThresholdServiceLevelIndicator extends ServiceLevelIndicator {
  String metric1;
  Double thresholdValue;
  ThresholdType thresholdType;

  @Override
  public SLIMetricType getSLIMetricType() {
    return SLIMetricType.THRESHOLD;
  }

  @Override
  public SLIEvaluationType getSLIEvaluationType() {
    return SLIEvaluationType.WINDOW;
  }

  @Override
  public List<String> getMetricNames() {
    List<String> metricForRatioSLI = new ArrayList<>();
    metricForRatioSLI.add(metric1);
    return metricForRatioSLI;
  }

  public static class ThresholdServiceLevelIndicatorUpdatableEntity
      extends ServiceLevelIndicatorUpdatableEntity<ThresholdServiceLevelIndicator, ThresholdServiceLevelIndicator> {
    @Override
    public void setUpdateOperations(UpdateOperations<ThresholdServiceLevelIndicator> updateOperations,
        ThresholdServiceLevelIndicator thresholdServiceLevelIndicator) {
      setCommonOperations(updateOperations, thresholdServiceLevelIndicator);
      updateOperations.set(ThresholdServiceLevelIndicatorKeys.metric1, thresholdServiceLevelIndicator.getMetric1());
      updateOperations.set(
          ThresholdServiceLevelIndicatorKeys.thresholdValue, thresholdServiceLevelIndicator.getThresholdValue());
      updateOperations.set(
          ThresholdServiceLevelIndicatorKeys.thresholdType, thresholdServiceLevelIndicator.getThresholdType());
    }
  }

  public boolean isUpdatable(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      Preconditions.checkArgument(isCoreUpdatable(serviceLevelIndicator));
      ThresholdServiceLevelIndicator thresholdServiceLevelIndicator =
          (ThresholdServiceLevelIndicator) serviceLevelIndicator;
      Preconditions.checkArgument(this.getMetric1().equalsIgnoreCase(thresholdServiceLevelIndicator.getMetric1()));
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  @Override
  public boolean shouldReAnalysis(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      ThresholdServiceLevelIndicator thresholdServiceLevelIndicator =
          (ThresholdServiceLevelIndicator) serviceLevelIndicator;
      Preconditions.checkArgument(this.getThresholdValue().equals(thresholdServiceLevelIndicator.getThresholdValue()));
      Preconditions.checkArgument(this.getThresholdType().equals(thresholdServiceLevelIndicator.getThresholdType()));
      return false;
    } catch (IllegalArgumentException ex) {
      return true;
    }
  }
}
