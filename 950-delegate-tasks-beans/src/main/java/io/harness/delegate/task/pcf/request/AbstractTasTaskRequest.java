/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(CDP)
@NoArgsConstructor
@AllArgsConstructor
public class AbstractTasTaskRequest implements CfCommandRequestNG {
  @Getter @Setter Integer timeoutIntervalInMin;
  @Getter @Setter public String accountId;
  @Getter @Setter String commandName;
  @Getter @Setter CfCommandTypeNG cfCommandTypeNG;
  @Getter @Setter CommandUnitsProgress commandUnitsProgress;
  @Getter @Setter @NotNull TasInfraConfig tasInfraConfig;
  @Getter @Setter boolean useCfCLI;
  @Getter @Setter CfCliVersion cfCliVersion;
}
