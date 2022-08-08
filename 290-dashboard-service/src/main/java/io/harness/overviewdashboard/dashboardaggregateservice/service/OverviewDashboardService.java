/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.overviewdashboard.dashboardaggregateservice.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dashboards.SortBy;
import io.harness.overviewdashboard.dtos.CountOverview;
import io.harness.overviewdashboard.dtos.DeploymentsStatsOverview;
import io.harness.overviewdashboard.dtos.ExecutionResponse;
import io.harness.overviewdashboard.dtos.TopProjectsPanel;
import io.harness.pms.dashboards.GroupBy;

@OwnedBy(HarnessTeam.PL)
public interface OverviewDashboardService {
  ExecutionResponse<TopProjectsPanel> getTopProjectsPanel(
      String accountIdentifier, String userId, long startInterval, long endInterval);

  ExecutionResponse<DeploymentsStatsOverview> getDeploymentStatsOverview(
      String accountIdentifier, String userId, long startInterval, long endInterval, GroupBy groupBy, SortBy sortBy);

  ExecutionResponse<CountOverview> getCountOverview(
      String accountIdentifier, String userId, long startInterval, long endInterval);
}
