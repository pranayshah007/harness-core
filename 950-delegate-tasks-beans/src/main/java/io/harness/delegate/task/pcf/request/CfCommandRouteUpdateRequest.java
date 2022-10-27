/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.pcf.model.CfCliVersion;

import lombok.Builder;
import lombok.Data;

/**
 * This class contains all required data for PCFCommandTask.SETUP to perform setup task
 */
@Data
@OwnedBy(CDP)
public class CfCommandRouteUpdateRequest extends CfCommandRequest {
  private CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData;

  @Builder
  public CfCommandRouteUpdateRequest(String accountId, String appId, String commandName, String activityId,
      PcfCommandType pcfCommandType, String organization, String space, CfInternalConfig pcfConfig,
      String workflowExecutionId, Integer timeoutIntervalInMin, CfRouteUpdateRequestConfigData pcfRouteUpdateConfigData,
      boolean useCfCLI, boolean useAppAutoscalar, boolean enforceSslValidation, boolean limitPcfThreads,
      CfCliVersion cfCliVersion) {
    super(accountId, appId, commandName, activityId, pcfCommandType, organization, space, pcfConfig,
        workflowExecutionId, timeoutIntervalInMin, useCfCLI, enforceSslValidation, useAppAutoscalar, limitPcfThreads,
        cfCliVersion);
    this.pcfRouteUpdateConfigData = pcfRouteUpdateConfigData;
  }
}
