/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.analysis.CVAnalysisConstants.TIMESERIES_SERVICE_GUARD_DATA_LENGTH;
import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.DeviationType;
import io.harness.cvng.beans.ThresholdConfigType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdActionType;
import io.harness.cvng.beans.TimeSeriesThresholdCriteria;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdActionType;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdCriteriaType;
import io.harness.cvng.core.constant.MonitoredServiceConstants;
import io.harness.cvng.core.transformer.metricThresholdSpec.MetricThresholdSpecDTOTransformer;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.models.VerificationType;

import com.google.gson.Gson;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "MetricCVConfigKeys")
@EqualsAndHashCode(callSuper = true)
public abstract class MetricCVConfig<I extends AnalysisInfo> extends CVConfig {
  private MetricPack metricPack;
  public abstract Optional<String> maybeGetGroupName();
  public abstract List<I> getMetricInfos();
  public abstract void setMetricInfos(List<I> metricInfos);

  public TimeRange getFirstTimeDataCollectionTimeRange() {
    Instant endTime = DateTimeUtils.roundDownTo5MinBoundary(getFirstTimeDataCollectionStartTime());
    return TimeRange.builder()
        .startTime(endTime.minus(TIMESERIES_SERVICE_GUARD_DATA_LENGTH, ChronoUnit.MINUTES))
        .endTime(endTime)
        .build();
  }
  @Override
  public VerificationType getVerificationType() {
    return VerificationType.TIME_SERIES;
  }

  @Override
  public void validate() {
    super.validate();
    checkNotNull(metricPack, generateErrorMessageFromParam(MetricCVConfigKeys.metricPack));
  }

  @Override
  public boolean queueAnalysisForPreDeploymentTask() {
    return false;
  }

  public abstract static class MetricCVConfigUpdatableEntity<T extends MetricCVConfig, D extends MetricCVConfig>
      extends CVConfigUpdatableEntity<T, D> {
    protected void setCommonOperations(UpdateOperations<T> updateOperations, D metricCVConfig) {
      super.setCommonOperations(updateOperations, metricCVConfig);
      updateOperations.set(MetricCVConfigKeys.metricPack, metricCVConfig.getMetricPack());
    }
  }

  public Set<TimeSeriesThreshold> getThresholdsToCreateOnSaveForCustomProviders(
      String metricName, TimeSeriesMetricType metricType, List<TimeSeriesThresholdType> thresholdTypes) {
    Set<TimeSeriesThreshold> thresholds = new HashSet<>();
    if (Objects.nonNull(metricType) && Objects.nonNull(thresholdTypes)) {
      metricType.getThresholds().forEach(threshold -> {
        thresholdTypes.forEach(type -> {
          Gson gson = new Gson();
          TimeSeriesThresholdCriteria criteria =
              gson.fromJson(gson.toJson(threshold), TimeSeriesThresholdCriteria.class);
          criteria.setThresholdType(type);
          thresholds.add(TimeSeriesThreshold.builder()
                             .accountId(getAccountId())
                             .projectIdentifier(getProjectIdentifier())
                             .dataSourceType(getType())
                             .metricType(metricType)
                             .metricName(metricName)
                             .action(TimeSeriesThresholdActionType.IGNORE)
                             .criteria(criteria)
                             .thresholdConfigType(ThresholdConfigType.DEFAULT)
                             .deviationType(DeviationType.getDeviationType(thresholdTypes))
                             .build());
        });
      });
    }
    return thresholds;
  }

  public List<TimeSeriesThresholdType> getThresholdTypeOfMetric(String metricName, MetricCVConfig cvConfig) {
    Set<TimeSeriesThresholdType> thresholdTypes = new HashSet<>();
    for (MetricPack.MetricDefinition metricDefinition : cvConfig.getMetricPack().getMetrics()) {
      if (metricDefinition.getName().equals(metricName)) {
        if (Objects.nonNull(metricDefinition.getThresholds())) {
          metricDefinition.getThresholds().forEach(
              threshold -> thresholdTypes.add(threshold.getCriteria().getThresholdType()));
        }
      }
    }
    return new ArrayList<>(thresholdTypes);
  }

  public List<TimeSeriesMetricPackDTO.MetricThreshold> getMetricThresholdDTOs() {
    List<TimeSeriesMetricPackDTO.MetricThreshold> metricThresholds = new ArrayList<>();
    Map<String, List<TimeSeriesThreshold>> mapOfTimeSeriesThreshold = new HashMap<>();
    metricPack.getMetrics().forEach(metricDefinition -> {
      if (!isEmpty(metricDefinition.getThresholds())) {
        List<TimeSeriesThreshold> customThresholds =
            metricDefinition.getThresholds()
                .stream()
                .filter(m -> ThresholdConfigType.USER_DEFINED.equals(m.getThresholdConfigType()))
                .collect(Collectors.toList());
        for (TimeSeriesThreshold timeSeriesThreshold : customThresholds) {
          String key = getKey(timeSeriesThreshold);
          List<TimeSeriesThreshold> timeSeriesThresholds =
              mapOfTimeSeriesThreshold.getOrDefault(key, new ArrayList<>());
          timeSeriesThresholds.add(timeSeriesThreshold);
          mapOfTimeSeriesThreshold.put(key, timeSeriesThresholds);
        }
      }
    });

    for (Map.Entry<String, List<TimeSeriesThreshold>> entry : mapOfTimeSeriesThreshold.entrySet()) {
      List<TimeSeriesThreshold> customThresholds = entry.getValue();
      MetricThresholdCriteriaType thresholdCriteriaType =
          MetricThresholdCriteriaType.getTimeSeriesThresholdComparisonType(
              customThresholds.get(0).getCriteria().getType());
      Optional<TimeSeriesThreshold> lessThanTimeSeriesThreshold =
          customThresholds.stream()
              .filter(m -> TimeSeriesThresholdType.ACT_WHEN_LOWER.equals(m.getCriteria().getThresholdType()))
              .findFirst();
      Optional<TimeSeriesThreshold> greaterThanTimeSeriesThreshold =
          customThresholds.stream()
              .filter(m -> TimeSeriesThresholdType.ACT_WHEN_HIGHER.equals(m.getCriteria().getThresholdType()))
              .findFirst();
      Double lessThan = null;
      Double greaterThan = null;
      if (lessThanTimeSeriesThreshold.isPresent()) {
        double value = thresholdCriteriaType.getPercentage(lessThanTimeSeriesThreshold.get().getCriteria().getValue());
        if (lessThanTimeSeriesThreshold.get().getAction().equals(TimeSeriesThresholdActionType.IGNORE)) {
          greaterThan = value;
        } else {
          lessThan = value;
        }
      }
      if (greaterThanTimeSeriesThreshold.isPresent()) {
        double value =
            thresholdCriteriaType.getPercentage(greaterThanTimeSeriesThreshold.get().getCriteria().getValue());
        if (greaterThanTimeSeriesThreshold.get().getAction().equals(TimeSeriesThresholdActionType.IGNORE)) {
          lessThan = value;
        } else {
          greaterThan = value;
        }
      }
      TimeSeriesThreshold baseMetricThreshold = customThresholds.get(0);
      metricThresholds.add(
          TimeSeriesMetricPackDTO.MetricThreshold.builder()
              .metricName(baseMetricThreshold.getMetricName())
              .groupName(maybeGetGroupName().orElse(baseMetricThreshold.getMetricGroupName()))
              .metricIdentifier(baseMetricThreshold.getMetricIdentifier())
              .type(MetricThresholdActionType.getMetricThresholdActionType(customThresholds.get(0).getAction()))
              .spec(MetricThresholdSpecDTOTransformer.getDto(baseMetricThreshold))
              .criteria(
                  TimeSeriesMetricPackDTO.MetricThreshold.MetricThresholdCriteria.builder()
                      .type(thresholdCriteriaType)
                      .spec(TimeSeriesMetricPackDTO.MetricThreshold.MetricThresholdCriteria.MetricThresholdCriteriaSpec
                                .builder()
                                .lessThan(lessThan)
                                .greaterThan(greaterThan)
                                .build())
                      .build())
              .build());
    }
    if (isNotEmpty(metricThresholds)) {
      return metricThresholds;
    }
    return null;
  }

  private String getKey(TimeSeriesThreshold timeSeriesThreshold) {
    return timeSeriesThreshold.getMetricName() + timeSeriesThreshold.getMetricGroupName()
        + timeSeriesThreshold.getAction() + timeSeriesThreshold.getCriteria().getType()
        + timeSeriesThreshold.getCriteria().getAction();
  }

  protected DeviationType getDeviationType(Map<String, HealthSourceMetricDefinition> mapOfMetricDefinitions,
      String metricName, MetricPack.MetricDefinition metric, String identifier) {
    if (!identifier.equalsIgnoreCase(MonitoredServiceConstants.CUSTOM_METRIC_PACK)) {
      return metric.getType().getDeviationType();
    } else {
      List<TimeSeriesThresholdType> thresholdTypes = null;
      if (mapOfMetricDefinitions.containsKey(metricName)) {
        thresholdTypes = mapOfMetricDefinitions.get(metricName).getRiskProfile().getThresholdTypes();
      }
      return DeviationType.getDeviationType(thresholdTypes);
    }
  }
}
