/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_GITOPS})
@Data
@Builder
@RecasterAlias("io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters")
public class ServiceStepV3Parameters implements StepParameters {
  private ParameterField<String> serviceRef;
  private ParameterField<Map<String, Object>> inputs;
  private ParameterField<String> envRef;
  // Supporting single infra in overrides V2 - First Phase
  private ParameterField<String> infraId;
  private ParameterField<String> envGroupRef;
  private List<ParameterField<String>> envRefs;
  private ParameterField<Boolean> gitOpsMultiSvcEnvEnabled;
  private ParameterField<Map<String, Object>> envInputs;
  private Map<String, ParameterField<Map<String, Object>>> envToEnvInputs;
  private Map<String, ParameterField<Map<String, Object>>> envToSvcOverrideInputs;
  private ParameterField<Map<String, Object>> serviceOverrideInputs;

  private List<String> childrenNodeIds;
  private ServiceDefinitionType deploymentType;
  @SkipAutoEvaluation private EnvironmentGroupYaml environmentGroupYaml;
  @SkipAutoEvaluation private EnvironmentsYaml environmentsYaml;

  @Override
  public List<String> excludeKeysFromStepInputs() {
    return new LinkedList<>(Arrays.asList("inputs", "envRef", "infraId", "envGroupRef", "envRefs",
        "gitOpsMultiSvcEnvEnabled", "envInputs", "envToEnvInputs", "envToSvcOverrideInputs", "serviceOverrideInputs",
        "childrenNodeIds", "deploymentType", "environmentGroupYaml", "environmentsYaml"));
  }
}
