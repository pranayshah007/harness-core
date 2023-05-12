/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
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

@JsonTypeName("RATIO")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "RatioServiceLevelIndicatorKeys")
@EqualsAndHashCode(callSuper = true)
public class RatioServiceLevelIndicator extends ServiceLevelIndicator {
  RatioSLIMetricEventType eventType;
  String metric1;
  String metric2;
  Double thresholdValue;
  ThresholdType thresholdType;

  Integer considerConsecutiveMinutes;

  Boolean considerAllConsecutiveMinutesFromStartAsBad;

  @Override
  public SLIMetricType getSLIMetricType() {
    return SLIMetricType.RATIO;
  }

  @Override
  public SLIEvaluationType getSLIEvaluationType() {
    return SLIEvaluationType.WINDOW;
  }

  @Override
  public List<String> getMetricNames() {
    List<String> metricForRatioSLI = new ArrayList<>();
    metricForRatioSLI.add(metric1);
    metricForRatioSLI.add(metric2);
    return metricForRatioSLI;
  }

  public static class RatioServiceLevelIndicatorUpdatableEntity
      extends ServiceLevelIndicatorUpdatableEntity<RatioServiceLevelIndicator, RatioServiceLevelIndicator> {
    @Override
    public void setUpdateOperations(UpdateOperations<RatioServiceLevelIndicator> updateOperations,
        RatioServiceLevelIndicator ratioServiceLevelIndicator) {
      setCommonOperations(updateOperations, ratioServiceLevelIndicator);
      updateOperations.set(RatioServiceLevelIndicatorKeys.eventType, ratioServiceLevelIndicator.getEventType())
          .set(RatioServiceLevelIndicatorKeys.metric1, ratioServiceLevelIndicator.getMetric1())
          .set(RatioServiceLevelIndicatorKeys.metric2, ratioServiceLevelIndicator.getMetric2())
          .set(RatioServiceLevelIndicatorKeys.thresholdValue, ratioServiceLevelIndicator.getThresholdValue())
          .set(RatioServiceLevelIndicatorKeys.thresholdType, ratioServiceLevelIndicator.getThresholdType())
          .set(ServiceLevelIndicatorKeys.sliMissingDataType, ratioServiceLevelIndicator.getSliMissingDataType());
      if (ratioServiceLevelIndicator.getConsiderConsecutiveMinutes() != null) {
        updateOperations.set(RatioServiceLevelIndicatorKeys.considerConsecutiveMinutes,
            ratioServiceLevelIndicator.getConsiderConsecutiveMinutes());
      } else {
        updateOperations.unset(RatioServiceLevelIndicatorKeys.considerConsecutiveMinutes);
      }
      if (ratioServiceLevelIndicator.considerAllConsecutiveMinutesFromStartAsBad != null) {
        updateOperations.set(RatioServiceLevelIndicatorKeys.considerAllConsecutiveMinutesFromStartAsBad,
            ratioServiceLevelIndicator.getConsiderAllConsecutiveMinutesFromStartAsBad());
      } else {
        updateOperations.unset(RatioServiceLevelIndicatorKeys.considerAllConsecutiveMinutesFromStartAsBad);
      }
    }
  }

  public boolean isUpdatable(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      Preconditions.checkArgument(isCoreUpdatable(serviceLevelIndicator));
      RatioServiceLevelIndicator ratioServiceLevelIndicator = (RatioServiceLevelIndicator) serviceLevelIndicator;
      Preconditions.checkArgument(this.getMetric1().equalsIgnoreCase(ratioServiceLevelIndicator.getMetric1()));
      Preconditions.checkArgument(this.getMetric2().equalsIgnoreCase(ratioServiceLevelIndicator.getMetric2()));
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  @Override
  public boolean shouldReAnalysis(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      Preconditions.checkArgument(this.getSliMissingDataType().equals(serviceLevelIndicator.getSliMissingDataType()));
      RatioServiceLevelIndicator ratioServiceLevelIndicator = (RatioServiceLevelIndicator) serviceLevelIndicator;
      Preconditions.checkArgument(this.getEventType().equals(ratioServiceLevelIndicator.getEventType()));
      Preconditions.checkArgument(this.getThresholdValue().equals(ratioServiceLevelIndicator.getThresholdValue()));
      Preconditions.checkArgument(this.getThresholdType().equals(ratioServiceLevelIndicator.getThresholdType()));
      if (this.getConsiderConsecutiveMinutes() != null
          || ratioServiceLevelIndicator.getConsiderConsecutiveMinutes() != null) {
        Preconditions.checkArgument(this.getConsiderConsecutiveMinutes() != null);
        Preconditions.checkArgument(ratioServiceLevelIndicator.getConsiderConsecutiveMinutes() != null);
        Preconditions.checkArgument(
            this.getConsiderConsecutiveMinutes().equals(ratioServiceLevelIndicator.getConsiderConsecutiveMinutes()));
      }
      if (this.getConsiderAllConsecutiveMinutesFromStartAsBad() != null
          || ratioServiceLevelIndicator.getConsiderAllConsecutiveMinutesFromStartAsBad() != null) {
        Preconditions.checkArgument(this.getConsiderAllConsecutiveMinutesFromStartAsBad() != null);
        Preconditions.checkArgument(
            ratioServiceLevelIndicator.getConsiderAllConsecutiveMinutesFromStartAsBad() != null);
        Preconditions.checkArgument(this.getConsiderAllConsecutiveMinutesFromStartAsBad().equals(
            ratioServiceLevelIndicator.getConsiderAllConsecutiveMinutesFromStartAsBad()));
      }
      return false;
    } catch (IllegalArgumentException ex) {
      return true;
    }
  }
}
