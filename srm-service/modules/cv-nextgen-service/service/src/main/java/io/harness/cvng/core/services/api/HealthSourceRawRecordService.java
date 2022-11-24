/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsRequest;
import io.harness.cvng.core.beans.healthsource.HealthSourceRecordsResponse;
import io.harness.cvng.core.beans.healthsource.LogRecordsResponse;
import io.harness.cvng.core.beans.healthsource.MetricRecordsResponse;
import io.harness.cvng.core.beans.healthsource.QueryRecordsRequest;
/**
 * The HealthSourceRawRecordService fetches metric data and log records from different health sources using delegates.
 */
public interface HealthSourceRawRecordService {
  /**
   * Fetch Sample records from the third party providers, for the purpose of displaying them.
   * @param healthSourceRecordsRequest details to fetch records from health sources.
   * @param account accountId
   * @param org organisationId
   * @param project projectId
   * @return raw healthSource records
   */
  HealthSourceRecordsResponse fetchSampleRawRecordsForHealthSource(
      HealthSourceRecordsRequest healthSourceRecordsRequest, String account, String org, String project);

  /**
   * Fetch sample time-series data, from which charts can be plotted.One or more time-series can be returned.
   * @param queryRecordsRequest details required to fetch metric data from health sources.
   * @param account accountId
   * @param org organisationId
   * @param project projectId
   * @return list of time-series metrics.
   */
  MetricRecordsResponse fetchMetricData(
      QueryRecordsRequest queryRecordsRequest, String account, String org, String project);

  /**
   * Fetch sample log table data, the data is tagged with the hostname.
   * @param queryRecordsRequest details required to fetch log table data from health sources.
   * @param account accountId
   * @param org organisationId
   * @param project projectId
   * @return list of log records
   */
  LogRecordsResponse fetchLogData(QueryRecordsRequest queryRecordsRequest, String account, String org, String project);
}
