/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.expression.Expression;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class CfBasicSetupRequestNG extends AbstractTasTaskRequest {
  String releaseNamePrefix;
  TasArtifactConfig tasArtifactConfig;
  Integer olderActiveVersionCountToKeep;
  Integer maxCount;
  Integer currentRunningCount;
  boolean useCurrentCount;
  @Expression(ALLOW_SECRETS) List<String> routeMaps;
  boolean useAppAutoScalar;
  PcfManifestsPackage pcfManifestsPackage;

  @Builder
  public CfBasicSetupRequestNG(String accountId, CfCommandTypeNG cfCommandTypeNG, String commandName,
      CommandUnitsProgress commandUnitsProgress, TasInfraConfig tasInfraConfig, boolean useCfCLI,
      CfCliVersion cfCliVersion, Integer timeoutIntervalInMin, String releaseNamePrefix,
      TasArtifactConfig tasArtifactConfig, Integer olderActiveVersionCountToKeep, Integer maxCount,
      Integer currentRunningCount, boolean useCurrentCount, List<String> routeMaps, boolean useAppAutoScalar,
      PcfManifestsPackage pcfManifestsPackage) {
    super(timeoutIntervalInMin, accountId, commandName, cfCommandTypeNG, commandUnitsProgress, tasInfraConfig, useCfCLI,
        cfCliVersion);
    this.releaseNamePrefix = releaseNamePrefix;
    this.tasArtifactConfig = tasArtifactConfig;
    this.olderActiveVersionCountToKeep = olderActiveVersionCountToKeep;
    this.maxCount = maxCount;
    this.currentRunningCount = currentRunningCount;
    this.useCurrentCount = useCurrentCount;
    this.routeMaps = routeMaps;
    this.useAppAutoScalar = useAppAutoScalar;
    this.pcfManifestsPackage = pcfManifestsPackage;
  }
}
