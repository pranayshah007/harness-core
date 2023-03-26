/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas.outcome;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasInstanceCountType;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.expression.Expression;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("TasSetupDataOutcome")
@JsonTypeName("TasSetupDataOutcome")
@RecasterAlias("io.harness.cdng.tas.outcome.TasSetupDataOutcome")
public class TasSetupDataOutcome implements Outcome, ExecutionSweepingOutput {
  Integer totalPreviousInstanceCount;
  Integer desiredActualFinalCount;
  String newReleaseName;
  Integer maxCount;
  List<CfServiceData> instanceData;
  TasResizeStrategyType resizeStrategy;
  String cfAppNamePrefix;
  CfCliVersion cfCliVersion;
  Integer timeoutIntervalInMinutes;
  TasApplicationInfo inActiveApplicationDetails;
  TasApplicationInfo activeApplicationDetails;
  TasApplicationInfo newApplicationDetails;
  List<String> tempRouteMap;
  List<String> routeMaps;
  TasInstanceCountType instanceCountType;
  @Builder.Default Boolean isBlueGreen = Boolean.FALSE;
  boolean useAppAutoScalar;
  @Expression(ALLOW_SECRETS) TasManifestsPackage manifestsPackage;
}
