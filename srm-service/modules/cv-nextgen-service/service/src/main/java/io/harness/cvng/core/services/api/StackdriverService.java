/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDTO;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDetail;
import io.harness.ng.beans.PageResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public interface StackdriverService extends DataSourceConnectivityChecker {
  PageResponse<StackdriverDashboardDTO> listDashboards(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, int pageSize, int offset, String filter, String tracingId);
  List<StackdriverDashboardDetail> getDashboardDetails(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, String path, String tracingId);

  List<LinkedHashMap> getSampleLogData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String query, String tracingId);

  Set<TimeSeriesSampleDTO> getSampleData(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, Object metricDefinitionDTO, String tracingId);
}
