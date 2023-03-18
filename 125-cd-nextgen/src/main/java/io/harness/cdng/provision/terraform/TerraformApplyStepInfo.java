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
import io.harness.cdng.provision.terraform.TerraformApplyStepParameters.TerraformApplyStepParametersBuilder;
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
import io.harness.walktree.visitor.Visitable;

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
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("terraformApplyStepInfo")
@JsonTypeName(StepSpecTypeConstants.TERRAFORM_APPLY)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OneOfField(fields = {"terraformStepConfiguration", "terraformCloudCliStepConfiguration"})
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformApplyStepInfo")
public class TerraformApplyStepInfo
    extends TerraformApplyBaseStepInfo implements CDAbstractStepInfo, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @JsonProperty("configuration") TerraformStepConfiguration terraformStepConfiguration;
  @JsonProperty("cloudCliConfiguration") TerraformCloudCliStepConfiguration terraformCloudCliStepConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformApplyStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, TerraformStepConfiguration terraformStepConfiguration,
      TerraformCloudCliStepConfiguration terraformCloudCliStepConfiguration) {
    super(provisionerIdentifier, delegateSelectors);
    this.terraformStepConfiguration = terraformStepConfiguration;
    this.terraformCloudCliStepConfiguration = terraformCloudCliStepConfiguration;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return TerraformApplyStep.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParams();

    TerraformApplyStepParametersBuilder builder = TerraformApplyStepParameters.infoBuilder();

    builder.provisionerIdentifier(getProvisionerIdentifier());
    builder.delegateSelectors(getDelegateSelectors());

    if (terraformStepConfiguration != null) {
      builder.configuration(terraformStepConfiguration.toStepParameters());
    } else if (terraformCloudCliStepConfiguration != null) {
      builder.configuration(terraformCloudCliStepConfiguration.toStepParameters());
    }

    return builder.build();
  }

  void validateSpecParams() {
    if (terraformStepConfiguration != null) {
      Validator.notNullCheck("Terraform Step configuration is null", terraformStepConfiguration);
      terraformStepConfiguration.validateParams();
    }
    if (terraformCloudCliStepConfiguration != null) {
      Validator.notNullCheck("Terraform Step configuration is null", terraformCloudCliStepConfiguration);
      terraformCloudCliStepConfiguration.validateParams();
    }
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();

    if (terraformStepConfiguration != null) {
      if (terraformStepConfiguration.terraformStepConfigurationType == TerraformStepConfigurationType.INLINE) {
        TerraformExecutionData terraformExecutionData = terraformStepConfiguration.terraformExecutionData;

        connectorRefMap.put("configuration.spec.configFiles.store.spec.connectorRef",
            terraformExecutionData.getTerraformConfigFilesWrapper().store.getSpec().getConnectorReference());

        List<TerraformVarFileWrapper> terraformVarFiles = terraformExecutionData.getTerraformVarFiles();
        extractConnectorRefFromVarFile(connectorRefMap, terraformVarFiles, "configuration");
      }
    }

    else if (terraformCloudCliStepConfiguration != null) {
      TerraformCloudCliExecutionData terraformCloudCliExecutionData =
          terraformCloudCliStepConfiguration.terraformCloudCliExecutionData;
      connectorRefMap.put("cloudCliConfiguration.spec.configFiles.store.spec.connectorRef",
          terraformCloudCliExecutionData.getTerraformConfigFilesWrapper().store.getSpec().getConnectorReference());

      List<TerraformVarFileWrapper> terraformVarFiles = terraformCloudCliExecutionData.getTerraformVarFiles();
      extractConnectorRefFromVarFile(connectorRefMap, terraformVarFiles, "cloudCliConfiguration");
    }
    return connectorRefMap;
  }

  private void extractConnectorRefFromVarFile(Map<String, ParameterField<String>> connectorRefMap,
      List<TerraformVarFileWrapper> terraformVarFiles, String configName) {
    if (EmptyPredicate.isNotEmpty(terraformVarFiles)) {
      for (TerraformVarFileWrapper terraformVarFile : terraformVarFiles) {
        if (terraformVarFile.getVarFile().getType().equals(TerraformVarFileTypes.Remote)) {
          connectorRefMap.put(String.format("%s.spec.varFiles." + terraformVarFile.getVarFile().identifier
                                      + ".spec.store.spec.connectorRef",
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
