/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1.StepElementParametersV1Builder;
import io.harness.plancreator.steps.common.v1.StepParametersUtilsV1;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.steps.StepSpecTypeConstantsV1;
import io.harness.steps.StepUtils;
import io.harness.yaml.utils.v1.NGVariablesUtilsV1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Builder
@JsonTypeName(StepSpecTypeConstantsV1.SHELL_SCRIPT)
@OwnedBy(PIPELINE)
public class ShellScriptStepNodeV1 extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstantsV1.SHELL_SCRIPT;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true) ShellScriptStepInfoV1 spec;

  // TODO: set rollback parameters
  public StepElementParametersV1 getStepParameters(PlanCreationContext ctx) {
    StepElementParametersV1Builder stepBuilder = StepParametersUtilsV1.getStepParameters(this);
    stepBuilder.spec(getSpecParameters());
    stepBuilder.type(StepSpecTypeConstantsV1.SHELL_SCRIPT);
    StepUtils.appendDelegateSelectorsToSpecParameters(spec, ctx);
    return stepBuilder.build();
  }

  public SpecParameters getSpecParameters() {
    return ShellScriptStepParameters.infoBuilder()
        .executionTarget(spec.getExecution_target())
        .onDelegate(spec.getOn_delegate())
        .output_vars(NGVariablesUtilsV1.getMapOfVariablesWithoutSecretExpression(
            spec.getOutput_vars() != null ? spec.getOutput_vars().getMap() : null))
        .env_vars(
            NGVariablesUtilsV1.getMapOfVariables(spec.getEnv_vars() != null ? spec.getEnv_vars().getMap() : null, 0L))
        .secret_output_vars(NGVariablesUtilsV1.getSetOfSecretVars(
            spec.getOutput_vars() != null ? spec.getOutput_vars().getMap() : null))
        .shell(spec.getShell())
        .source(spec.getSource())
        .delegate(spec.getDelegate())
        .include_infra_selectors(spec.getInclude_infra_selectors())
        .outputAlias(spec.getOutput_alias())
        .build();
  }
}
