/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@TypeAlias("environmentStepParameters")
@RecasterAlias("io.harness.cdng.environment.steps.EnvironmentStepParameters")
public class EnvironmentStepParameters implements StepParameters {
  String childNodeID;
  // Environment Basic Info
  String name;
  String identifier;
  String description;
  EnvironmentType type;
  Map<String, String> tags;
  ParameterField<String> environmentRef;
  ParameterField<String> envGroupRef;
  Map<String, Object> variables;
  Map<String, Object> serviceOverrides;
}
