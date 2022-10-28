/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.PrometheusSampleData;
import io.harness.cvng.core.beans.params.PrometheusConnectionParams;

import java.util.List;

public interface PrometheusService {
  List<String> getMetricNames(String accountId, String orgIdentifier, String projectIdentifier, String tracingId,
      PrometheusConnectionParams prometheusConnectionParams);
  List<String> getLabelNames(String accountId, String orgIdentifier, String projectIdentifier, String tracingId,
      PrometheusConnectionParams prometheusConnectionParams);
  List<String> getLabelValues(String accountId, String orgIdentifier, String projectIdentifier, String labelName,
      String tracingId, PrometheusConnectionParams prometheusConnectionParams);
  List<PrometheusSampleData> getSampleData(String accountId, String orgIdentifier, String projectIdentifier,
      String query, String tracingId, PrometheusConnectionParams prometheusConnectionParams);
}
