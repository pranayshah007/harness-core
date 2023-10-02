/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common.pipeline;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.CollectionUtils;
import io.harness.plancreator.flowcontrol.FlowControlConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.utils.CommonPlanCreatorUtils;
import io.harness.yaml.core.properties.NGProperties;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@NoArgsConstructor
@TypeAlias("pipelineSetupStepParameters")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.common.pipeline.PipelineSetupStepParameters")
public class PipelineSetupStepParameters implements StepParameters {
  String childNodeID;

  String name;
  String identifier;
  FlowControlConfig flowControl;
  ParameterField<String> description;
  Map<String, String> tags;
  ParameterField<Map<String, Object>> properties;
  @SkipAutoEvaluation ParameterField<Map<String, Object>> variables;

  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  String executionId;

  @Builder(builderMethodName = "newBuilder")
  public PipelineSetupStepParameters(String childNodeID, String name, String identifier, FlowControlConfig flowControl,
      ParameterField<String> description, Map<String, String> tags, NGProperties properties,
      List<NGVariable> originalVariables, String executionId, int sequenceId,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    this.childNodeID = childNodeID;
    this.name = name;
    this.identifier = identifier;
    this.flowControl = flowControl;
    this.description = description;
    this.tags = CollectionUtils.emptyIfNull(tags);
    this.properties = ParameterField.createValueField(NGVariablesUtils.getMapOfNGProperties(properties));
    this.variables = ParameterField.createValueField(NGVariablesUtils.getMapOfVariables(originalVariables));
    this.executionId = executionId;
    this.delegateSelectors = delegateSelectors;
  }

  public static PipelineSetupStepParameters getStepParameters(
      PlanCreationContext ctx, PipelineInfoConfig infoConfig, String childNodeID) {
    if (infoConfig == null) {
      return PipelineSetupStepParameters.newBuilder()
          .childNodeID(childNodeID)
          .executionId(ctx.getExecutionUuid())
          .build();
    }
    CommonPlanCreatorUtils.validateVariables(infoConfig.getVariables(),
        "Execution Input is not allowed for pipeline variables as it is similar to making it a runtime input");
    TagUtils.removeUuidFromTags(infoConfig.getTags());

    return new PipelineSetupStepParameters(childNodeID, infoConfig.getName(), infoConfig.getIdentifier(),
        infoConfig.getFlowControl(), SdkCoreStepUtils.getParameterFieldHandleValueNull(infoConfig.getDescription()),
        infoConfig.getTags(), infoConfig.getProperties(), infoConfig.getVariables(), ctx.getExecutionUuid(),
        ctx.getRunSequence(), infoConfig.getDelegateSelectors());
  }
}
