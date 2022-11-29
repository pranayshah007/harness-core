/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.newrelic.NewRelicApplication;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.params.ProjectParams;

import java.util.LinkedHashMap;
import java.util.List;

public interface NewRelicService {
  List<String> getNewRelicEndpoints();
  List<NewRelicApplication> getNewRelicApplications(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String filter, String tracingId);
  MetricPackValidationResponse validateData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String appName, String appId, List<MetricPackDTO> metricPacks, String tracingId);
  LinkedHashMap fetchSampleData(
      ProjectParams projectParams, String connectorIdentifier, String query, String tracingId);
}
