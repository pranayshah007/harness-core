/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesThresholdActionType;
import io.harness.cvng.beans.TimeSeriesThresholdCriteria;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.core.entities.TimeSeriesThreshold.TimeSeriesThresholdKeys;
import io.harness.cvng.core.services.api.TimeSeriesThresholdService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class TimeSeriesThresholdServiceImpl implements TimeSeriesThresholdService {
  @Inject private HPersistence hPersistence;

  @Override
  public boolean deleteMetricPackThresholds(
      String accountId, String orgIdentifier, String projectIdentifier, String thresholdId) {
    return hPersistence.delete(TimeSeriesThreshold.class, thresholdId);
  }

  @Override
  public List<String> saveMetricPackThreshold(String accountId, String orgIdentifier, String projectIdentifier,
      DataSourceType dataSourceType, List<TimeSeriesThreshold> timeSeriesThresholds) {
    timeSeriesThresholds.forEach(timeSeriesThreshold -> {
      timeSeriesThreshold.setAccountId(accountId);
      timeSeriesThreshold.setOrgIdentifier(orgIdentifier);
      timeSeriesThreshold.setProjectIdentifier(projectIdentifier);
      timeSeriesThreshold.setDataSourceType(dataSourceType);
    });
    return hPersistence.save(timeSeriesThresholds);
  }

  @Override
  public List<TimeSeriesThreshold> createDefaultIgnoreThresholds(String accountId, String orgIdentifier,
      String projectIdentifier, DataSourceType dataSourceType, MetricPack metricPack) {
    Preconditions.checkNotNull(
        metricPack, "No metric pack found for project and pack ", projectIdentifier, metricPack.getIdentifier());

    List<TimeSeriesThreshold> timeSeriesThresholds = new ArrayList<>();
    metricPack.getMetrics().forEach(metricDefinition -> {
      final List<TimeSeriesThresholdCriteria> thresholds = metricDefinition.getType().getThresholds();
      thresholds.forEach(threshold
          -> timeSeriesThresholds.add(TimeSeriesThreshold.builder()
                                          .accountId(accountId)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .dataSourceType(dataSourceType)
                                          .metricType(metricDefinition.getType())
                                          .metricPackIdentifier(metricPack.getIdentifier())
                                          .metricName(metricDefinition.getName())
                                          .metricIdentifier(metricDefinition.getIdentifier())
                                          .action(TimeSeriesThresholdActionType.IGNORE)
                                          .deviationType(threshold.getDeviationType())
                                          .criteria(threshold)
                                          .build()));
    });
    saveMetricPackThreshold(accountId, orgIdentifier, projectIdentifier, dataSourceType, timeSeriesThresholds);
    return timeSeriesThresholds;
  }

  @Override
  public List<TimeSeriesThreshold> getMetricPackThresholds(String accountId, String orgIdentifier,
      String projectIdentifier, MetricPack metricPack, DataSourceType dataSourceType) {
    List<TimeSeriesThreshold> timeSeriesThresholds =
        hPersistence.createQuery(TimeSeriesThreshold.class, excludeAuthority)
            .filter(TimeSeriesThresholdKeys.accountId, accountId)
            .filter(TimeSeriesThresholdKeys.orgIdentifier, orgIdentifier)
            .filter(TimeSeriesThresholdKeys.projectIdentifier, projectIdentifier)
            .filter(TimeSeriesThresholdKeys.metricPackIdentifier, metricPack.getIdentifier())
            .filter(TimeSeriesThresholdKeys.dataSourceType, dataSourceType)
            .asList();
    if (isEmpty(timeSeriesThresholds)) {
      return createDefaultIgnoreThresholds(accountId, orgIdentifier, projectIdentifier, dataSourceType, metricPack);
    }
    return timeSeriesThresholds;
  }
}
