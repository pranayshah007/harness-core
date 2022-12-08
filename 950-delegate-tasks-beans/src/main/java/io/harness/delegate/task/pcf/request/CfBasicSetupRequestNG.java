/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
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
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfBasicSetupRequestNG implements CfCommandRequestNG {
  String accountId;
  CfCommandTypeNG cfCommandTypeNG;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  String releaseNamePrefix;
  boolean isPackageArtifact;
  TasArtifactConfig tasArtifactConfig;
  @NotNull TasInfraConfig tasInfraConfig;
  boolean useCfCLI;
  @NotNull CfCliVersion cfCliVersion;
  String manifestYaml;
  Integer timeoutIntervalInMin;
  Integer olderActiveVersionCountToKeep;
  Integer maxCount;
  Integer currentRunningCount;
  boolean useCurrentCount;
  @Expression(ALLOW_SECRETS) List<String> routeMaps;
  boolean useAppAutoscalar;
  PcfManifestsPackage pcfManifestsPackage;
}
