/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.SLIExecutionType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;

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

@JsonTypeName("REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "RequestServiceLevelIndicatorKeys")
@EqualsAndHashCode(callSuper = true)
public class RequestServiceLevelIndicator extends ServiceLevelIndicator {
  RatioSLIMetricEventType eventType;
  String metric1;
  String metric2;
  @Override
  public SLIMetricType getSLIMetricType() {
    return null;
  }

  @Override
  public SLIExecutionType getSLIExecutionType() {
    return SLIExecutionType.REQUEST;
  }

  @Override
  public List<String> getMetricNames() {
    List<String> metricForRatioSLI = new ArrayList<>();
    metricForRatioSLI.add(metric1);
    metricForRatioSLI.add(metric2);
    return metricForRatioSLI;
  }

  @Override
  public boolean isUpdatable(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      Preconditions.checkNotNull(serviceLevelIndicator);
      Preconditions.checkArgument(
          this.getHealthSourceIdentifier().equals(serviceLevelIndicator.getHealthSourceIdentifier()));
      Preconditions.checkArgument(
          this.getMonitoredServiceIdentifier().equals(serviceLevelIndicator.getMonitoredServiceIdentifier()));
      Preconditions.checkArgument(isCoreUpdatable(serviceLevelIndicator));
      RequestServiceLevelIndicator requestServiceLevelIndicator = (RequestServiceLevelIndicator) serviceLevelIndicator;
      Preconditions.checkArgument(this.getMetric1().equalsIgnoreCase(requestServiceLevelIndicator.getMetric1()));
      Preconditions.checkArgument(this.getMetric2().equalsIgnoreCase(requestServiceLevelIndicator.getMetric2()));
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  @Override
  public boolean shouldReAnalysis(ServiceLevelIndicator serviceLevelIndicator) {
    try {
      RequestServiceLevelIndicator requestServiceLevelIndicator = (RequestServiceLevelIndicator) serviceLevelIndicator;
      Preconditions.checkArgument(this.getEventType().equals(requestServiceLevelIndicator.getEventType()));
      return false;
    } catch (IllegalArgumentException ex) {
      return true;
    }
  }

  public static class RequestServiceLevelIndicatorUpdatableEntity
      extends ServiceLevelIndicatorUpdatableEntity<RequestServiceLevelIndicator, RequestServiceLevelIndicator> {
    @Override
    public void setUpdateOperations(UpdateOperations<RequestServiceLevelIndicator> updateOperations,
        RequestServiceLevelIndicator requestServiceLevelIndicator) {
      setCommonOperations(updateOperations, requestServiceLevelIndicator);
      updateOperations.set(RequestServiceLevelIndicatorKeys.eventType, requestServiceLevelIndicator.getEventType())
          .set(RequestServiceLevelIndicatorKeys.metric1, requestServiceLevelIndicator.getMetric1())
          .set(RequestServiceLevelIndicatorKeys.metric2, requestServiceLevelIndicator.getMetric2());
    }
  }
}
