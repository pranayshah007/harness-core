/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.expression.Expression;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Value
@Builder(toBuilder = true)
@OwnedBy(CDP)
@TypeAlias("tasExecutionPassThroughData")
@RecasterAlias("io.harness.cdng.tas.TasExecutionPassThroughData")
public class TasExecutionPassThroughData implements PassThroughData {
  String applicationName;
  UnitProgressData lastActiveUnitProgressData;
  String zippedManifestId;
  @Expression(ALLOW_SECRETS) TasManifestsPackage tasManifestsPackage;
  @Expression(ALLOW_SECRETS) TasManifestsPackage unresolvedTasManifestsPackage;
  Map<String, String> allFilesFetched;
  String repoRoot;
  CfCliVersionNG cfCliVersion;
  String rawScript;
  List<String> commandUnits;
  List<String> pathsFromScript;
  @Setter @NonFinal Map<String, String> resolvedOutputVariables;
  int desiredCountInFinalYaml;
}
