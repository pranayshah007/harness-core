/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.AppdynamicsValidationResponse;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.appd.AppDynamicsApplication;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition;
import io.harness.cvng.beans.appd.AppDynamicsTier;
import io.harness.cvng.beans.appd.AppdynamicsMetricDataResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.ng.beans.PageResponse;

import java.util.List;
import java.util.Set;

@OwnedBy(CV)
public interface AppDynamicsService extends DataSourceConnectivityChecker {
  Set<AppdynamicsValidationResponse> getMetricPackData(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, String appName, String tierName, String requestGuid,
      List<MetricPackDTO> metricPacks);

  PageResponse<AppDynamicsApplication> getApplications(String accountId, String connectorIdentifier,
      String orgIdentifier, String projectIdentifier, int offset, int pageSize, String filter);

  PageResponse<AppDynamicsTier> getTiers(String accountId, String connectorIdentifier, String orgIdentifier,
      String projectIdentifier, String appName, int offset, int pageSize, String filter);

  List<String> getBaseFolders(
      ProjectParams projectParams, String connectorIdentifiers, String appName, String path, String tracingId);

  List<AppDynamicsFileDefinition> getMetricStructure(ProjectParams projectParams, String connectorIdentifier,
      String appName, String baseFolder, String tier, String metricPath, String tracingId);

  @Deprecated(since = "moved to v2")
  AppdynamicsMetricDataResponse getMetricData(ProjectParams projectParams, String connectorIdentifier, String appName,
      String baseFolder, String tier, String metricPath, String tracingId);

  @Deprecated(since = "moved to getCompleteServiceInstanceMetricPath")
  String getServiceInstanceMetricPath(ProjectParams projectParams, String connectorIdentifier, String appName,
      String baseFolder, String tier, String metricPath, String tracingId);

  AppdynamicsMetricDataResponse getMetricDataV2(ProjectParams projectParams, String connectorIdentifier, String appName,
      String completeMetricPath, String tracingId);

  String getCompleteServiceInstanceMetricPath(ProjectParams projectParams, String connectorIdentifier, String appName,
      String completeMetricPath, String tracingId);
}
