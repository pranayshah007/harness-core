/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This class contains all required data for PCFCommandTask.DEPLOY to perform setup task
 */
@Data
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
public class CfCommandDeployRequest extends CfCommandRequest {
  private String newReleaseName;
  private List<String> routeMaps;

  /**
   * This is not desired count but update count, means upsize new app by currentCount + 5,
   * delegating calculating actual desiredInstanceCount to PCFCommandTask
   * (delegate), makes sure in all deploy state, we calculate based on most current data.
   *
   */
  private Integer updateCount;
  private Integer downSizeCount;
  private Integer totalPreviousInstanceCount;
  private CfAppSetupTimeDetails downsizeAppDetail;
  private Integer maxCount;
  private PcfManifestsPackage pcfManifestsPackage;
  /**
   * This will be empty for deploy_state, so deploy will figureOut old versions and scale them down by 5
   * This will be set by Rollback, Rollback will use same request and PCFCommand.DEPLOY,
   * and looking at this list, we will know its coming from deploy state or rollback state
   */
  private List<CfServiceData> instanceData;
  private ResizeStrategy resizeStrategy;
  private boolean isStandardBlueGreen;

  @Builder
  public CfCommandDeployRequest(String accountId, String appId, String commandName, String activityId,
      PcfCommandType pcfCommandType, String organization, String space, CfInternalConfig pcfConfig,
      String workflowExecutionId, String newReleaseName, Integer maxCount, Integer updateCount, Integer downSizeCount,
      Integer totalPreviousInstanceCount, List<CfServiceData> instanceData, ResizeStrategy resizeStrategy,
      List<String> routeMaps, Integer timeoutIntervalInMin, boolean useCfCLI, CfAppSetupTimeDetails downsizeAppDetail,
      boolean isStandardBlueGreen, PcfManifestsPackage pcfManifestsPackage, boolean useAppAutoscalar,
      boolean enforceSslValidation, boolean limitPcfThreads, CfCliVersion cfCliVersion) {
    super(accountId, appId, commandName, activityId, pcfCommandType, organization, space, pcfConfig,
        workflowExecutionId, timeoutIntervalInMin, useCfCLI, enforceSslValidation, useAppAutoscalar, limitPcfThreads,
        cfCliVersion);
    this.newReleaseName = newReleaseName;
    this.updateCount = updateCount;
    this.downSizeCount = downSizeCount;
    this.instanceData = instanceData;
    this.resizeStrategy = resizeStrategy;
    this.routeMaps = routeMaps;
    this.totalPreviousInstanceCount = totalPreviousInstanceCount;
    this.downsizeAppDetail = downsizeAppDetail;
    this.isStandardBlueGreen = isStandardBlueGreen;
    this.maxCount = maxCount;
    this.pcfManifestsPackage = pcfManifestsPackage;
  }
}
