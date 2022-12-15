/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class CfSwapRoutesRequestNG extends AbstractTasTaskRequest {
  String newApplicationName;
  List<String> tempRoutes;
  List<String> finalRoutes;
  boolean downsizeOldApplication;
  TasApplicationInfo activeApplicationDetails;
  TasApplicationInfo inActiveApplicationDetails;
  TasApplicationInfo newApplicationDetails;
  List<String> existingApplicationNames;
  String releaseNamePrefix;
  boolean useAppAutoScalar;
  Integer olderActiveVersionCountToKeep;

  @Builder
  public CfSwapRoutesRequestNG(String accountId, CfCommandTypeNG cfCommandTypeNG, String commandName,
      CommandUnitsProgress commandUnitsProgress, TasInfraConfig tasInfraConfig, boolean useCfCLI,
      CfCliVersion cfCliVersion, Integer timeoutIntervalInMin, String releaseNamePrefix,
      Integer olderActiveVersionCountToKeep, List<String> finalRoutes, boolean useAppAutoScalar,
      List<String> tempRoutes, boolean downsizeOldApplication, TasApplicationInfo activeApplicationDetails,
      TasApplicationInfo inActiveApplicationDetails, TasApplicationInfo newApplicationDetails,
      List<String> existingApplicationNames, String newApplicationName) {
    super(timeoutIntervalInMin, accountId, commandName, cfCommandTypeNG, commandUnitsProgress, tasInfraConfig, useCfCLI,
        cfCliVersion);

    this.newApplicationName = newApplicationName;
    this.tempRoutes = tempRoutes;
    this.finalRoutes = finalRoutes;
    this.downsizeOldApplication = downsizeOldApplication;
    this.activeApplicationDetails = activeApplicationDetails;
    this.inActiveApplicationDetails = inActiveApplicationDetails;
    this.newApplicationDetails = newApplicationDetails;
    this.existingApplicationNames = existingApplicationNames;
    this.releaseNamePrefix = releaseNamePrefix;
    this.useAppAutoScalar = useAppAutoScalar;
    this.olderActiveVersionCountToKeep = olderActiveVersionCountToKeep;
  }
}
