/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.Dashboard;

import static io.harness.NGDateUtils.DAY_IN_MS;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.service.PipelineDashboardService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelineDashboardOverviewResourceV2Impl implements PipelineDashboardOverviewResourceV2 {
  private final PipelineDashboardService pipelineDashboardService;
  @NGAccessControlCheck(resourceType = "PROJECT", permission = "core_project_view")
  public ResponseDTO<DashboardPipelineHealthInfo> fetchPipelinedHealth(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ResourceIdentifier String projectIdentifier, @NotNull String pipelineIdentifier,
      @NotNull String moduleInfo, @NotNull long startInterval, @NotNull long endInterval) {
    log.info("Getting pipeline health");
    long previousInterval = startInterval - (endInterval - startInterval + DAY_IN_MS);

    return ResponseDTO.newResponse(
        pipelineDashboardService.getDashboardPipelineHealthInfo(accountIdentifier, orgIdentifier, projectIdentifier,
            pipelineIdentifier, startInterval, endInterval, previousInterval, moduleInfo));
  }

  @NGAccessControlCheck(resourceType = "PROJECT", permission = "core_project_view")
  public ResponseDTO<DashboardPipelineExecutionInfo> getPipelineDashboardExecution(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ResourceIdentifier String projectIdentifier, @NotNull String pipelineIdentifier,
      @NotNull String moduleInfo, @NotNull long startInterval, @NotNull long endInterval) {
    log.info("getting pipeline execution");
    return ResponseDTO.newResponse(pipelineDashboardService.getDashboardPipelineExecutionInfo(accountIdentifier,
        orgIdentifier, projectIdentifier, pipelineIdentifier, startInterval, endInterval, moduleInfo));
  }
}
