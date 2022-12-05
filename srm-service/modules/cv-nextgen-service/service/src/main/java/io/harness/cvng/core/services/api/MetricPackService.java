/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.TimeSeriesThreshold;

import java.util.List;

public interface MetricPackService {
  List<MetricPackDTO> getMetricPacks(
      DataSourceType dataSourceType, String accountId, String orgIdentifier, String projectIdentifier);

  List<MetricPack> getMetricPacks(
      String accountId, String orgIdentifier, String projectIdentifier, DataSourceType dataSourceType);

  MetricPack getMetricPack(String accountId, String orgIdentifier, String projectIdentifier,
      DataSourceType dataSourceType, String identifier);

  void createDefaultMetricPackAndThresholds(String accountId, String orgIdentifier, String projectIdentifier);

  boolean saveMetricPacks(String accountId, String orgIdentifier, String projectIdentifier,
      DataSourceType dataSourceType, List<MetricPack> metricPacks);

  List<TimeSeriesThreshold> getMetricPackThresholds(String accountId, String orgIdentifier, String projectIdentifier,
      String metricPackIdentifier, DataSourceType dataSourceType);

  void populatePaths(String accountId, String orgIdentifier, String projectIdentifier, DataSourceType dataSourceType,
      MetricPack metricPack);

  void populateDataCollectionDsl(DataSourceType dataSourceType, MetricPack metricPack);
}
