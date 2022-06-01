/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.Validator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@JsonTypeName(StepSpecTypeConstants.TERRAFORM_PLAN)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformPlanStepInfo")
public class TerraformPlanStepInfo extends TerraformPlanBaseStepInfo implements CDStepInfo, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("configuration") TerraformPlanExecutionData terraformPlanExecutionData;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformPlanStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, TerraformPlanExecutionData terraformPlanExecutionData) {
    super(provisionerIdentifier, delegateSelectors);
    this.terraformPlanExecutionData = terraformPlanExecutionData;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return TerraformPlanStep.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParams();
    return TerraformPlanStepParameters.infoBuilder()
        .provisionerIdentifier(provisionerIdentifier)
        .delegateSelectors(delegateSelectors)
        .configuration(terraformPlanExecutionData.toStepParameters())
        .build();
  }

  void validateSpecParams() {
    Validator.notNullCheck("Terraform Plan configuration is NULL", terraformPlanExecutionData);
    terraformPlanExecutionData.validateParams();
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    validateSpecParams();
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put("configuration.configFiles.store.spec.connectorRef",
        terraformPlanExecutionData.getTerraformConfigFilesWrapper().store.getSpec().getConnectorReference());

    List<TerraformVarFileWrapper> terraformVarFiles = terraformPlanExecutionData.getTerraformVarFiles();

    if (EmptyPredicate.isNotEmpty(terraformVarFiles)) {
      for (TerraformVarFileWrapper terraformVarFile : terraformVarFiles) {
        if (terraformVarFile.getVarFile().getType().equals(TerraformVarFileTypes.Remote)) {
          connectorRefMap.put(
              "configuration.varFiles." + terraformVarFile.getVarFile().identifier + ".spec.store.spec.connectorRef",
              ((RemoteTerraformVarFileSpec) terraformVarFile.varFile.spec).store.getSpec().getConnectorReference());
        }
      }
    }

    connectorRefMap.put("configuration.secretManagerRef", terraformPlanExecutionData.getSecretManagerRef());
    return connectorRefMap;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
