/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.datadog.DatadogDashboardDTO;
import io.harness.cvng.core.beans.datadog.DatadogDashboardDetail;
import io.harness.cvng.core.beans.datadog.MetricTagResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.ng.beans.PageResponse;

import java.util.LinkedHashMap;
import java.util.List;

public interface DatadogService extends DataSourceConnectivityChecker {
  PageResponse<DatadogDashboardDTO> getAllDashboards(ProjectParams projectParams, String connectorIdentifier,
      int pageSize, int offset, String filter, String tracingId);

  List<DatadogDashboardDetail> getDashboardDetails(
      ProjectParams projectParams, String connectorIdentifier, String dashboardId, String tracingId);
  @Deprecated
  List<String> getMetricTagsList(
      ProjectParams projectParams, String connectorIdentifier, String metricName, String tracingId);

  List<String> getActiveMetrics(
      ProjectParams projectParams, String connectorIdentifier, String filter, String tracingId);

  List<TimeSeriesSampleDTO> getTimeSeriesPoints(
      ProjectParams projectParams, String connectorIdentifier, String query, String tracingId);

  List<LinkedHashMap> getSampleLogData(
      ProjectParams projectParams, String connectorIdentifier, String query, String tracingId);

  List<String> getLogIndexes(ProjectParams projectParams, String connectorIdentifier, String tracingId);

  MetricTagResponseDTO getMetricTagsResponse(
      ProjectParams projectParams, String connectorIdentifier, String metricName, String filter, String tracingId);
}
