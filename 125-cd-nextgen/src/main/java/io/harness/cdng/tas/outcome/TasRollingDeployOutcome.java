/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas.outcome;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.execution.tas.TasStageExecutionDetails;
import io.harness.expression.Expression;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("TasRollingDeployOutcome")
@JsonTypeName("TasRollingDeployOutcome")
@RecasterAlias("io.harness.cdng.tas.outcome.TasRollingDeployOutcome")
public class TasRollingDeployOutcome implements Outcome, ExecutionSweepingOutput {
  @Setter @NonFinal String appName;
  @Setter @NonFinal String appGuid;
  @Setter @NonFinal Integer timeoutIntervalInMin;
  @Setter @NonFinal boolean isFirstDeployment;
  @Setter @NonFinal CfCliVersionNG cfCliVersion;
  @Setter @NonFinal boolean deploymentStarted;
  @Expression(ALLOW_SECRETS) @Setter @NonFinal TasStageExecutionDetails tasStageExecutionDetails;
  @Expression(ALLOW_SECRETS) @Setter @NonFinal List<String> routeMaps;
}
