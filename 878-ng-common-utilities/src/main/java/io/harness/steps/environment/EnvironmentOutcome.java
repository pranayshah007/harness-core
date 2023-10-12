/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.environment;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Value
@Builder
@JsonTypeName("environmentOutcome")
@RecasterAlias("io.harness.steps.environment.EnvironmentOutcome")
@OwnedBy(CDC)
public class EnvironmentOutcome implements Outcome, ExecutionSweepingOutput {
  String name;
  String identifier;
  String description;
  EnvironmentType type;

  // v1Type is being added to support compatibility with FG env.Type
  String v1Type;

  Map<String, String> tags;

  // EnvironmentOutcomeV2
  String environmentRef;
  Map<String, Object> variables;

  String envGroupRef;
  String envGroupName;
}
