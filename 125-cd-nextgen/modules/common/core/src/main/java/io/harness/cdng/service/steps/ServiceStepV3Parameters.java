/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceStepV3Parameters implements StepParameters {
  private ParameterField<String> serviceRef;
  private ParameterField<Map<String, Object>> inputs;
  private ParameterField<String> envRef;
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
  public String toViewJson() {
    return RecastOrchestrationUtils.toJson(Map.of("service", serviceRef.fetchFinalValue()));
  }
}
