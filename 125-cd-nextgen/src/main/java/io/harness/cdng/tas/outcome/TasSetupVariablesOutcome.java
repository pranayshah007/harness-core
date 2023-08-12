/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("TasSetupVariablesOutcome")
@JsonTypeName("TasSetupVariablesOutcome")
@RecasterAlias("io.harness.cdng.tas.outcome.TasSetupVariablesOutcome")
public class TasSetupVariablesOutcome implements Outcome, ExecutionSweepingOutput {
  @Setter @NonFinal String newAppName;
  @Setter @NonFinal String newAppGuid;
  @Setter @NonFinal List<String> newAppRoutes;
  @Setter @NonFinal String oldAppName;
  @Setter @NonFinal String oldAppGuid;
  @Setter @NonFinal List<String> oldAppRoutes;
  @Setter @NonFinal List<String> finalRoutes;
  @Setter @NonFinal List<String> tempRoutes;
  @Setter @NonFinal String activeAppName;
  @Setter @NonFinal String inActiveAppName;
}
