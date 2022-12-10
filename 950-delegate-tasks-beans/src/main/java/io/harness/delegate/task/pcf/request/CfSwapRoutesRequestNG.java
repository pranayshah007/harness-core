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
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCliVersionNG;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRoutesRequestNG implements CfCommandRequestNG {
  String accountId;
  CfCommandTypeNG cfCommandTypeNG;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  Integer timeoutIntervalInMin;
  TasInfraConfig tasInfraConfig;
  String newApplicationName;
  CfAppSetupTimeDetails existingApplicationDetails;
  List<String> existingApplicationNames;
  List<String> tempRoutes;
  List<String> finalRoutes;
  boolean downsizeOldApplication;
  boolean isMapRoutesOperation;
  boolean upSizeInActiveApp;
  CfAppSetupTimeDetails existingInActiveApplicationDetails;
  TasApplicationInfo newApplicationDetails;
  String cfAppNamePrefix;
  boolean useAppAutoscalar;
  CfCliVersion cfCliVersion;
}
