/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.TasCanaryAppSetupStepInfoVisitorHelper;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = TasCanaryAppSetupStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.TAS_CANARY_APP_SETUP)
@TypeAlias("tasCanaryAppSetupStepInfo")
@RecasterAlias("io.harness.cdng.tas.TasCanaryAppSetupStepInfo")
public class TasCanaryAppSetupStepInfo extends TasAppSetupBaseStepInfo implements CDAbstractStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
  @NotNull
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.RESIZE_STRATEGY_TAS_CLASSPATH)
  ParameterField<TasResizeStrategyType> resizeStrategy;
  @Builder(builderMethodName = "infoBuilder")
  public TasCanaryAppSetupStepInfo(TasInstanceCountType instanceCountType, ParameterField<String> existingVersionToKeep,
      ParameterField<List<String>> additionalRoutes, ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<TasResizeStrategyType> resizeStrategy) {
    super(instanceCountType, existingVersionToKeep, additionalRoutes, delegateSelectors);
    this.resizeStrategy = resizeStrategy;
  }

  @Override
  public StepType getStepType() {
    return TasCanaryAppSetupStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return TasCanaryAppSetupStepParameters.infoBuilder()
        .tasInstanceCountType(this.tasInstanceCountType)
        .existingVersionToKeep(this.existingVersionToKeep)
        .additionalRoutes(this.additionalRoutes)
        .resizeStrategy(this.resizeStrategy.getValue())
        .delegateSelectors(this.getDelegateSelectors())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
