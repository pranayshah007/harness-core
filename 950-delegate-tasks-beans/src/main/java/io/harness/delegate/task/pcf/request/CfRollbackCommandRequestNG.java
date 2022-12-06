/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@OwnedBy(HarnessTeam.CDP)
public class CfRollbackCommandRequestNG implements CfCommandRequestNG {
  String accountId;
  CfCommandTypeNG cfCommandTypeNG;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  Integer timeoutIntervalInMin;
  TasInfraConfig tasInfraConfig;
  List<CfServiceData> instanceData;
  List<String> routeMaps;
  List<String> tempRouteMaps;
  CfAppSetupTimeDetails oldApplicationDetails;
  CfAppSetupTimeDetails newApplicationDetails;
  String cfAppNamePrefix;
  Integer activeAppRevision;
  CfAppSetupTimeDetails existingInActiveApplicationDetails;
  CfCliVersion cfCliVersion;
  boolean enforceSslValidation;
  boolean useAppAutoscalar;
  boolean swapRouteOccured;
  String existingAppNamingStrategy;
  boolean upsizeInActiveApp;
  boolean downsizeOldApps;
}