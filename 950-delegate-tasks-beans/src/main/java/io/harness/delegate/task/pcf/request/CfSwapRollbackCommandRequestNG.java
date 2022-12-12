/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRollbackCommandRequestNG extends CfRollbackCommandRequestNG {
  List<CfAppSetupTimeDetails> existingApplicationDetails;

  public CfSwapRollbackCommandRequestNG(String accountId, CfCommandTypeNG pcfCommandType, String commandName,
      CommandUnitsProgress commandUnitsProgress, Integer timeoutIntervalInMin, TasInfraConfig tasInfraConfig,
      List<CfServiceData> instanceData, List<String> routeMaps, List<String> tempRouteMaps,
      TasApplicationInfo oldApplicationDetails, TasApplicationInfo newApplicationDetails, String cfAppNamePrefix,
      Integer activeAppRevision, CfAppSetupTimeDetails existingInActiveApplicationDetails, CfCliVersion cfCliVersion,
      boolean enforceSslValidation, boolean useAppAutoscalar, boolean swapRouteOccured, boolean limitPcfThreads,
      String existingAppNamingStrategy, boolean upsizeInActiveApp, boolean downsizeOldApps,
      List<CfAppSetupTimeDetails> existingApplicationDetails) {
    super(accountId, pcfCommandType, commandName, commandUnitsProgress, timeoutIntervalInMin, tasInfraConfig,
        instanceData, routeMaps, tempRouteMaps, oldApplicationDetails, newApplicationDetails, cfAppNamePrefix,
        activeAppRevision, existingInActiveApplicationDetails, cfCliVersion, enforceSslValidation, useAppAutoscalar,
        swapRouteOccured, existingAppNamingStrategy, upsizeInActiveApp, downsizeOldApps);
    this.existingApplicationDetails = existingApplicationDetails;
  }
}
