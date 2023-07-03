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
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.provision.terraform.TerraformPlanStepParameters.TerraformPlanStepParametersBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfField;
import io.harness.validation.Validator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@OneOfField(fields = {"terraformPlanExecutionData", "terraformCloudCliPlanExecutionData"})
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformPlanStepInfo")
public class TerraformPlanStepInfo extends TerraformPlanBaseStepInfo implements CDAbstractStepInfo, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @JsonProperty("configuration") TerraformPlanExecutionData terraformPlanExecutionData;
  @JsonProperty("cloudCliConfiguration") TerraformCloudCliPlanExecutionData terraformCloudCliPlanExecutionData;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformPlanStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, TerraformPlanExecutionData terraformPlanExecutionData,
      TerraformCloudCliPlanExecutionData terraformCloudCliPlanExecutionData) {
    super(provisionerIdentifier, delegateSelectors);
    this.terraformPlanExecutionData = terraformPlanExecutionData;
    this.terraformCloudCliPlanExecutionData = terraformCloudCliPlanExecutionData;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return TerraformPlanStepV2.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParams();

    TerraformPlanStepParametersBuilder config = TerraformPlanStepParameters.infoBuilder();

    config.provisionerIdentifier(provisionerIdentifier);
    config.delegateSelectors(delegateSelectors);

    if (terraformPlanExecutionData != null) {
      config.configuration(terraformPlanExecutionData.toStepParameters());
    } else if (terraformCloudCliPlanExecutionData != null) {
      config.configuration(terraformCloudCliPlanExecutionData.toStepParameters());
    }

    return config.build();
  }

  void validateSpecParams() {
    if (terraformPlanExecutionData != null) {
      Validator.notNullCheck("Terraform Plan configuration is NULL", terraformPlanExecutionData);

      terraformPlanExecutionData.validateParams();
    } else if (terraformCloudCliPlanExecutionData != null) {
      Validator.notNullCheck("Terraform Plan configuration is NULL", terraformCloudCliPlanExecutionData);
      terraformCloudCliPlanExecutionData.validateParams();
    }
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    validateSpecParams();
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();

    if (terraformPlanExecutionData != null) {
      connectorRefMap.put("configuration.configFiles.store.spec.connectorRef",
          terraformPlanExecutionData.getTerraformConfigFilesWrapper().store.getSpec().getConnectorReference());

      List<TerraformVarFileWrapper> terraformVarFiles = terraformPlanExecutionData.getTerraformVarFiles();
      extractConnectorRefFromVarFile(connectorRefMap, terraformVarFiles, "configuration");
      connectorRefMap.put("configuration.secretManagerRef", terraformPlanExecutionData.getSecretManagerRef());
    } else if (terraformCloudCliPlanExecutionData != null) {
      connectorRefMap.put("cloudCliConfiguration.configFiles.store.spec.connectorRef",
          terraformCloudCliPlanExecutionData.getTerraformConfigFilesWrapper().store.getSpec().getConnectorReference());

      List<TerraformVarFileWrapper> terraformVarFiles = terraformCloudCliPlanExecutionData.getTerraformVarFiles();
      extractConnectorRefFromVarFile(connectorRefMap, terraformVarFiles, "cloudCliConfiguration");
    }
    return connectorRefMap;
  }

  private void extractConnectorRefFromVarFile(Map<String, ParameterField<String>> connectorRefMap,
      List<TerraformVarFileWrapper> terraformVarFiles, String configName) {
    if (EmptyPredicate.isNotEmpty(terraformVarFiles)) {
      for (TerraformVarFileWrapper terraformVarFile : terraformVarFiles) {
        if (terraformVarFile.getVarFile().getType().equals(TerraformVarFileTypes.Remote)) {
          connectorRefMap.put(
              String.format("%s.varFiles." + terraformVarFile.getVarFile().identifier + ".spec.store.spec.connectorRef",
                  configName),
              ((RemoteTerraformVarFileSpec) terraformVarFile.varFile.spec).store.getSpec().getConnectorReference());
        }
      }
    }
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
