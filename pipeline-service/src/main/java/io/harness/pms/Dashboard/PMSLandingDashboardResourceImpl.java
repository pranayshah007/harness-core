/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.Dashboard;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.dashboards.LandingDashboardRequestPMS;
import io.harness.pms.dashboards.PipelinesCount;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PMSLandingDashboardResourceImpl implements PMSLandingDashboardResource {
  private final PMSLandingDashboardService pmsLandingDashboardService;

  @NGAccessControlCheck(resourceType = "ACCOUNT", permission = "core_account_view")
  public ResponseDTO<PipelinesCount> getPipelinesCount(@NotNull @AccountIdentifier String accountIdentifier,
      @NotNull long startInterval, @NotNull long endInterval,
      @NotNull LandingDashboardRequestPMS landingDashboardRequestPMS) {
    return ResponseDTO.newResponse(pmsLandingDashboardService.getPipelinesCount(
        accountIdentifier, landingDashboardRequestPMS.getOrgProjectIdentifiers(), startInterval, endInterval));
  }
}
