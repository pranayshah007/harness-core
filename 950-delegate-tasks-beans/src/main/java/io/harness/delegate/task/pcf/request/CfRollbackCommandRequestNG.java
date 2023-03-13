/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.PcfAutoScalarCapability;
import io.harness.delegate.beans.executioncapability.PcfInstallationCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class CfRollbackCommandRequestNG extends AbstractTasTaskRequest {
  List<CfServiceData> instanceData;
  @Expression(ALLOW_SECRETS) List<String> routeMaps;
  TasApplicationInfo activeApplicationDetails;
  TasApplicationInfo newApplicationDetails;
  TasApplicationInfo inActiveApplicationDetails;
  @Expression(ALLOW_SECRETS) String cfAppNamePrefix;
  Integer activeAppRevision;
  boolean enforceSslValidation;
  boolean useAppAutoScalar;
  String existingAppNamingStrategy;
  @Builder
  public CfRollbackCommandRequestNG(String accountId, CfCommandTypeNG cfCommandTypeNG, String commandName,
      CommandUnitsProgress commandUnitsProgress, TasInfraConfig tasInfraConfig, boolean useCfCLI,
      CfCliVersion cfCliVersion, Integer timeoutIntervalInMin,

      List<CfServiceData> instanceData, List<String> routeMaps, TasApplicationInfo activeApplicationDetails,
      TasApplicationInfo newApplicationDetails, String cfAppNamePrefix, Integer activeAppRevision,
      TasApplicationInfo inActiveApplicationDetails, boolean enforceSslValidation, boolean useAppAutoScalar,
      String existingAppNamingStrategy) {
    super(timeoutIntervalInMin, accountId, commandName, cfCommandTypeNG, commandUnitsProgress, tasInfraConfig, useCfCLI,
        cfCliVersion);
    this.instanceData = instanceData;
    this.activeApplicationDetails = activeApplicationDetails;
    this.newApplicationDetails = newApplicationDetails;
    this.cfAppNamePrefix = cfAppNamePrefix;
    this.activeAppRevision = activeAppRevision;
    this.routeMaps = routeMaps;
    this.useAppAutoScalar = useAppAutoScalar;
    this.inActiveApplicationDetails = inActiveApplicationDetails;
    this.enforceSslValidation = enforceSslValidation;
    this.existingAppNamingStrategy = existingAppNamingStrategy;
  }

  @Override
  public void populateRequestCapabilities(
      List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (useCfCLI || useAppAutoScalar) {
      capabilities.add(PcfInstallationCapability.builder()
                           .criteria(format("Checking that CF CLI version: %s is installed", cfCliVersion))
                           .version(cfCliVersion)
                           .build());
    }
    if (useAppAutoScalar) {
      capabilities.add(PcfAutoScalarCapability.builder()
                           .version(cfCliVersion)
                           .criteria("Checking that App Autoscaler plugin is installed")
                           .build());
    }
  }
}
