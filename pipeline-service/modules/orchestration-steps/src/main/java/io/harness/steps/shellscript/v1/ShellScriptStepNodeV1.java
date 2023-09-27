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
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.shellscript.ShellScriptStepInfo;
import io.harness.steps.shellscript.ShellScriptStepParameters;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Data
@JsonTypeName(StepSpecTypeConstants.SHELL_SCRIPT)
@OwnedBy(PIPELINE)
public class ShellScriptStepNodeV1 extends PmsAbstractStepNodeV1 {
  String type = StepSpecTypeConstants.SHELL_SCRIPT;

  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true) ShellScriptStepInfo spec;

  // TODO: set rollback parameters
  public StepElementParametersV1 getStepParameters(PlanCreationContext ctx) {
    StepElementParametersV1Builder stepBuilder = StepParametersUtilsV1.getStepParameters(this);
    stepBuilder.spec(getSpecParameters());
    stepBuilder.type(StepSpecTypeConstants.SHELL_SCRIPT);
    StepUtils.appendDelegateSelectorsToSpecParameters(spec, ctx);
    return stepBuilder.build();
  }

  public SpecParameters getSpecParameters() {
    return ShellScriptStepParameters.infoBuilder()
        .executionTarget(spec.getExecutionTarget())
        .onDelegate(spec.getOnDelegate())
        .outputVariables(NGVariablesUtils.getMapOfVariablesWithoutSecretExpression(spec.getOutputVariables()))
        .environmentVariables(NGVariablesUtils.getMapOfVariables(spec.getEnvironmentVariables(), 0L))
        .secretOutputVariables(NGVariablesUtils.getSetOfSecretVars(spec.getOutputVariables()))
        .shellType(spec.getShell())
        .source(spec.getSource())
        .delegateSelectors(spec.getDelegateSelectors())
        .includeInfraSelectors(spec.getIncludeInfraSelectors())
        .outputAlias(spec.getOutputAlias())
        .build();
  }
}
