/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.integration.node.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.ElastigroupBGStageSetupStepInfoVisitorHelper;
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
@SimpleVisitorHelper(helperClass = ElastigroupBGStageSetupStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.ELASTIGROUP_BG_STAGE_SETUP)
@TypeAlias("elastigroupBGStageSetupStepInfo")
@RecasterAlias("io.harness.cdng.elastigroup.ElastigroupBGStageSetupStepInfo")
public class ElastigroupBGStageSetupStepInfo
    extends ElastigroupBGStageSetupBaseStepInfo implements CDAbstractStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public ElastigroupBGStageSetupStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> name, List<LoadBalancer> loadBalancers, ElastigroupInstances instances,
      CloudProvider connectedCloudProvider) {
    super(delegateSelectors, name, loadBalancers, instances, connectedCloudProvider);
  }

  @Override
  public StepType getStepType() {
    return ElastigroupBGStageSetupStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return ElastigroupBGStageSetupStepParameters.infoBuilder()
        .delegateSelectors(this.getDelegateSelectors())
        .name(this.getName())
        .loadBalancers(this.getLoadBalancers())
        .instances(this.getInstances())
        .connectedCloudProvider(this.getConnectedCloudProvider())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
