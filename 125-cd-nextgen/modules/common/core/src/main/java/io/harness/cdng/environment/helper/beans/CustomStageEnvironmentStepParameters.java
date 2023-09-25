/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Data
@Builder
@RecasterAlias("io.harness.cdng.environment.helper.beans.CustomStageEnvironmentStepParameters")
public class CustomStageEnvironmentStepParameters implements StepParameters {
  private ParameterField<String> envRef;
  private ParameterField<String> infraId;
  private ParameterField<Map<String, Object>> envInputs;
  private List<String> childrenNodeIds;

  @Override
  public List<String> excludeKeysFromStepInputs() {
    return new LinkedList<>(Arrays.asList("envRef", "infraId", "envInputs", "childrenNodeIds"));
  }
}
