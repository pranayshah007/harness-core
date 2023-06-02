/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.provision.terraformcloud.steps.TerraformCloudRunStep;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("terraformCloudRunStepInfo")
@JsonTypeName(StepSpecTypeConstants.TERRAFORM_CLOUD_RUN)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.terraformcloud.TerraformCloudRunStepInfo")
public class TerraformCloudRunStepInfo implements CDAbstractStepInfo, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("runType") TerraformCloudRunType runType;

  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "runType", include = EXTERNAL_PROPERTY, visible = true)
  @Valid
  TerraformCloudRunExecutionSpec terraformCloudRunExecutionSpec;

  @JsonProperty("runMessage")
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> message;

  @YamlSchemaTypes(value = {expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformCloudRunStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      TerraformCloudRunExecutionSpec terraformCloudRunExecutionSpec, ParameterField<String> message) {
    this.delegateSelectors = delegateSelectors;
    this.terraformCloudRunExecutionSpec = terraformCloudRunExecutionSpec;
    this.message = message;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return TerraformCloudRunStep.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParams();
    return TerraformCloudRunStepParameters.infoBuilder()
        .delegateSelectors(delegateSelectors)
        .spec(terraformCloudRunExecutionSpec.getSpecParams())
        .message(message)
        .build();
  }

  void validateSpecParams() {
    terraformCloudRunExecutionSpec.validateParams();
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    validateSpecParams();
    return terraformCloudRunExecutionSpec.extractConnectorRefs();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
