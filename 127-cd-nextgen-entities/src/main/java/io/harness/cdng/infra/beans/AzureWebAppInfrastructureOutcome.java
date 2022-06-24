/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@Value
@SuperBuilder
@JsonTypeName(InfrastructureKind.AZURE_WEB_APP)
@TypeAlias("io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome")
public class AzureWebAppInfrastructureOutcome extends InfrastructureOutcomeAbstract implements InfrastructureOutcome {
  String connectorRef;
  EnvironmentOutcome environment;
  String infrastructureKey;
  String subscription;
  String resourceGroup;
  String webApp;
  String deploymentSlot;

  @Override
  public String getKind() {
    return InfrastructureKind.AZURE_WEB_APP;
  }
}
