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
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class CfDeployCommandRequestNG extends AbstractTasTaskRequest {
  String newReleaseName;
  List<String> routeMaps;
  Integer upsizeCount;
  Integer downSizeCount;
  Integer totalPreviousInstanceCount;
  TasApplicationInfo downsizeAppDetail;
  PcfManifestsPackage pcfManifestsPackage;
  Integer maxCount;
  List<CfServiceData> instanceData;
  ResizeStrategy resizeStrategy;
  boolean isStandardBlueGreen;
  boolean useAppAutoScalar;

  @Builder
  public CfDeployCommandRequestNG(String accountId, CfCommandTypeNG cfCommandTypeNG, String commandName,
      CommandUnitsProgress commandUnitsProgress, TasInfraConfig tasInfraConfig, boolean useCfCLI,
      CfCliVersion cfCliVersion, Integer timeoutIntervalInMin, String newReleaseName, List<String> routeMaps,

      Integer upsizeCount, Integer downSizeCount, Integer totalPreviousInstanceCount,
      TasApplicationInfo downsizeAppDetail, PcfManifestsPackage pcfManifestsPackage, Integer maxCount,
      List<CfServiceData> instanceData, ResizeStrategy resizeStrategy, boolean isStandardBlueGreen,
      boolean useAppAutoScalar) {
    super(timeoutIntervalInMin, accountId, commandName, cfCommandTypeNG, commandUnitsProgress, tasInfraConfig, useCfCLI,
        cfCliVersion);
    this.newReleaseName = newReleaseName;
    this.routeMaps = routeMaps;
    this.upsizeCount = upsizeCount;
    this.downSizeCount = downSizeCount;
    this.totalPreviousInstanceCount = totalPreviousInstanceCount;
    this.downsizeAppDetail = downsizeAppDetail;
    this.pcfManifestsPackage = pcfManifestsPackage;
    this.maxCount = maxCount;
    this.instanceData = instanceData;
    this.resizeStrategy = resizeStrategy;
    this.isStandardBlueGreen = isStandardBlueGreen;
    this.useAppAutoScalar = useAppAutoScalar;
  }
}
