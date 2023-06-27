/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.aws.sam.AwsSamDeployStepInfoVisitorHelper;
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
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
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
@SimpleVisitorHelper(helperClass = AwsSamDeployStepInfoVisitorHelper.class)
@JsonTypeName(StepSpecTypeConstants.AWS_SAM_DEPLOY)
@TypeAlias("awsSamDeployStepInfo")
@RecasterAlias("io.harness.cdng.aws.sam.AwsSamDeployStepInfo")
public class AwsSamDeployStepInfo extends AwsSamBaseStepInfo implements CDAbstractStepInfo, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> deployCommandOptions;

  @NotNull
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> stackName;

  @Builder(builderMethodName = "infoBuilder")
  public AwsSamDeployStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<String> image,
      ParameterField<String> connectorRef, ContainerResource resources,
      ParameterField<Map<String, String>> envVariables, ParameterField<Boolean> privileged,
      ParameterField<Integer> runAsUser, ParameterField<ImagePullPolicy> imagePullPolicy,
      ParameterField<List<String>> deployCommandOptions, ParameterField<String> stackName,
      ParameterField<String> samVersion) {
    super(delegateSelectors, image, connectorRef, resources, envVariables, privileged, runAsUser, imagePullPolicy,
        samVersion);
    this.deployCommandOptions = deployCommandOptions;
    this.stackName = stackName;
  }

  @Override
  public StepType getStepType() {
    return AwsSamDeployStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return AwsSamDeployStepParameters.infoBuilder()
        .image(getImage())
        .envVariables(getEnvVariables())
        .delegateSelectors(this.getDelegateSelectors())
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
