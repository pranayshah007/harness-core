/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.WAIT_STEP)
@SimpleVisitorHelper(helperClass = WaitStepInfoVisitorHelper.class)
@TypeAlias("WaitStepInfo")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.wait.WaitStepInfo")
public class WaitStepInfo implements PMSStepInfo, Visitable {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN)
  @VariableExpression(skipInnerObjectTraversal = true)
  @NotNull
  ParameterField<Timeout> duration;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @Builder(builderMethodName = "infoBuilder")
  public WaitStepInfo(ParameterField<Timeout> duration, String uuid) {
    this.duration = duration;
    this.uuid = uuid;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return StepSpecTypeConstants.WAIT_STEP_TYPE;
  }
  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.WAIT_STEP;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return WaitStepParameters.infoBuilder()
        .duration(ParameterField.createValueField(TimeoutUtils.getTimeoutString(getDuration())))
        .uuid(getUuid())
        .build();
  }

  public StepParameters getStepParameters(
      AbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    StepElementParametersBuilder stepParametersBuilder = getStepParameters(stepElementConfig, failRollbackParameters);
    StepUtils.appendDelegateSelectorsToSpecParameters(stepElementConfig.getStepSpecType(), ctx);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }

  public StepElementParametersBuilder getStepParameters(
      AbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters) {
    return getStepParameters((WaitStepNode) stepElementConfig);
  }

  public StepElementParametersBuilder getStepParameters(WaitStepNode stepElementConfig) {
    StepElementParametersBuilder stepBuilder = StepElementParameters.builder();
    stepBuilder.name(stepElementConfig.getName());
    stepBuilder.identifier(stepElementConfig.getIdentifier());
    stepBuilder.delegateSelectors(stepElementConfig.getDelegateSelectors());
    stepBuilder.description(stepElementConfig.getDescription());
    stepBuilder.skipCondition(stepElementConfig.getSkipCondition());
    stepBuilder.when(stepElementConfig.getWhen() != null ? stepElementConfig.getWhen().getValue() : null);
    stepBuilder.failureStrategies(
        stepElementConfig.getFailureStrategies() != null ? stepElementConfig.getFailureStrategies().getValue() : null);
    stepBuilder.type(stepElementConfig.getType());
    stepBuilder.uuid(stepElementConfig.getUuid());
    stepBuilder.enforce(stepElementConfig.getEnforce());

    return stepBuilder;
  }
}
