/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.http.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.http.HttpHeaderConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.http.v1.HttpBaseStepInfoV1;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.v1.NGVariableV1Wrapper;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName(StepSpecTypeConstantsV1.HTTP)
@OwnedBy(HarnessTeam.PIPELINE)
public class HttpStepInfoV1 extends HttpBaseStepInfoV1 implements Visitable, PMSStepInfo {
  NGVariableV1Wrapper output_vars;
  NGVariableV1Wrapper input_vars;
  List<HttpHeaderConfig> headers;
  ParameterField<String> cert;
  ParameterField<String> cert_key;
  ParameterField<List<TaskSelectorYaml>> delegate;

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstantsV1.HTTP_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }
}
