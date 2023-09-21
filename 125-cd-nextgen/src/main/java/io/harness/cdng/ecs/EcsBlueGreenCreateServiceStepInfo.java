/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.EcsBlueGreenCreateServiceStepInfoVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SimpleVisitorHelper(helperClass = EcsBlueGreenCreateServiceStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE)
@TypeAlias("ecsBlueGreenCreateServiceStepInfo")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepInfo")
public class EcsBlueGreenCreateServiceStepInfo
    extends EcsBlueGreenCreateServiceBaseStepInfo implements CDAbstractStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public EcsBlueGreenCreateServiceStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> loadBalancer, ParameterField<String> prodListener,
      ParameterField<String> prodListenerRuleArn, ParameterField<String> stageListener,
      ParameterField<String> stageListenerRuleArn, ParameterField<Boolean> sameAsAlreadyRunningInstances,
      ParameterField<Boolean> enableAutoscalingInSwapStep) {
    super(delegateSelectors, loadBalancer, prodListener, prodListenerRuleArn, stageListener, stageListenerRuleArn,
        sameAsAlreadyRunningInstances, enableAutoscalingInSwapStep);
  }

  @Override
  public StepType getStepType() {
    return EcsBlueGreenCreateServiceStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return EcsBlueGreenCreateServiceStepParameters.infoBuilder()
        .delegateSelectors(this.getDelegateSelectors())
        .loadBalancer(this.getLoadBalancer())
        .prodListener(this.getProdListener())
        .stageListener(this.getStageListener())
        .prodListenerRuleArn(this.getProdListenerRuleArn())
        .stageListenerRuleArn(this.getStageListenerRuleArn())
        .sameAsAlreadyRunningInstances(this.getSameAsAlreadyRunningInstances())
        .enableAutoScalingInSwapStep(this.getEnableAutoScalingInSwapStep())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
