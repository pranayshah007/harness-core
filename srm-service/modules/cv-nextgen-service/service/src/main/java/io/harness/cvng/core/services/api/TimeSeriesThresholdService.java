/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.TimeSeriesThreshold;

import java.util.List;

public interface TimeSeriesThresholdService {
  boolean deleteMetricPackThresholds(
      String accountId, String orgIdentifier, String projectIdentifier, String thresholdId);

  List<String> saveMetricPackThreshold(String accountId, String orgIdentifier, String projectIdentifier,
      DataSourceType dataSourceType, List<TimeSeriesThreshold> timeSeriesThresholds);

  List<TimeSeriesThreshold> createDefaultIgnoreThresholds(String accountId, String orgIdentifier,
      String projectIdentifier, DataSourceType dataSourceType, MetricPack metricPack);

  List<TimeSeriesThreshold> getMetricPackThresholds(String accountId, String orgIdentifier, String projectIdentifier,
      MetricPack metricPack, DataSourceType dataSourceType);
}
