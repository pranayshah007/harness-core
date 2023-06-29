/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.BuildActiveInfo;
import io.harness.app.beans.entities.BuildFailureInfo;
import io.harness.app.beans.entities.CICreditsResult;
import io.harness.app.beans.entities.CIUsageResult;
import io.harness.app.beans.entities.DashboardBuildExecutionInfo;
import io.harness.app.beans.entities.DashboardBuildRepositoryInfo;
import io.harness.app.beans.entities.DashboardBuildsActiveAndFailedInfo;
import io.harness.app.beans.entities.DashboardBuildsHealthInfo;
import io.harness.cimanager.dashboard.api.CIDashboardOverviewResource;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.dashboards.GroupBy;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class CIDashboardOverviewResourceImpl implements CIDashboardOverviewResource {
  private final CIOverviewDashboardService ciOverviewDashboardService;
  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  public ResponseDTO<DashboardBuildsHealthInfo> getBuildHealth(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long startInterval, long endInterval) {
    log.info("Getting build health");
    long previousInterval = startInterval - (endInterval - startInterval + DAY_IN_MS);

    return ResponseDTO.newResponse(ciOverviewDashboardService.getDashBoardBuildHealthInfoWithRate(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, previousInterval));
  }

  public ResponseDTO<DashboardBuildExecutionInfo> getBuildExecution(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, GroupBy groupby, long startInterval, long endInterval) {
    log.info("Getting build execution");
    return ResponseDTO.newResponse(ciOverviewDashboardService.getBuildExecutionBetweenIntervals(
        accountIdentifier, orgIdentifier, projectIdentifier, groupby, startInterval, endInterval));
  }

  public ResponseDTO<DashboardBuildRepositoryInfo> getRepositoryBuild(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long startInterval, long endInterval) {
    log.info("Getting build repository");
    long previousInterval = startInterval - (endInterval - startInterval + DAY_IN_MS);
    return ResponseDTO.newResponse(ciOverviewDashboardService.getDashboardBuildRepository(
        accountIdentifier, orgIdentifier, projectIdentifier, startInterval, endInterval, previousInterval));
  }

  public ResponseDTO<DashboardBuildsActiveAndFailedInfo> getActiveAndFailedBuild(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long days) {
    log.info("Getting builds details failed and active");
    List<BuildFailureInfo> failureInfos = ciOverviewDashboardService.getDashboardBuildFailureInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, days);
    List<BuildActiveInfo> activeInfos = ciOverviewDashboardService.getDashboardBuildActiveInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, days);

    return ResponseDTO.newResponse(
        DashboardBuildsActiveAndFailedInfo.builder().failed(failureInfos).active(activeInfos).build());
  }

  public ResponseDTO<CIUsageResult> getCIUsageData(String accountIdentifier, long timestamp) {
    log.info("Getting usage data");

    return ResponseDTO.newResponse(ciOverviewDashboardService.getCIUsageResult(accountIdentifier, timestamp));
  }

  public ResponseDTO<CICreditsResult> getCredits(String accountIdentifier, long startInterval, long endInterval) {
    log.info("Getting credits data");
    long credits = ciOverviewDashboardService.getHostedCreditUsage(accountIdentifier, startInterval, endInterval);
    return ResponseDTO.newResponse(CICreditsResult.builder().credits(credits).build());
  }
}
