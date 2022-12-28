/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas.outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("TasSetupVariablesOutcome")
@JsonTypeName("TasSetupVariablesOutcome")
@RecasterAlias("io.harness.cdng.tas.outcome.TasSetupVariablesOutcome")
public class TasRollingDeployOutcome implements Outcome, ExecutionSweepingOutput {
  @Setter @NonFinal String appName;
  @Setter @NonFinal String appGuid;
  @Setter @NonFinal Integer timeoutIntervalInMin;
}
