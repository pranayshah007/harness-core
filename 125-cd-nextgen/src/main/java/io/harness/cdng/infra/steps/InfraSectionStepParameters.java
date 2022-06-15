/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@TypeAlias("infraSectionStepParameters")
@RecasterAlias("io.harness.cdng.infra.steps.InfraSectionStepParameters")
public class InfraSectionStepParameters implements StepParameters {
  String childNodeID;
  private EnvironmentYaml environment;
  private ParameterField<String> environmentRef;
  private InfraUseFromStage useFromStage;
  private ParameterField<String> ref;
}
