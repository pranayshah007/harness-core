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
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.Validator;
import io.harness.walktree.visitor.Visitable;

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
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("terraformApplyStepInfo")
@JsonTypeName(StepSpecTypeConstants.TERRAFORM_APPLY)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformApplyStepInfo")
public class TerraformApplyStepInfo
    extends TerraformApplyBaseStepInfo implements Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("configuration") TerraformStepConfiguration terraformStepConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public TerraformApplyStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, TerraformStepConfiguration terraformStepConfiguration) {
    super(provisionerIdentifier, delegateSelectors);
    this.terraformStepConfiguration = terraformStepConfiguration;
  }

  void validateSpecParams() {
    Validator.notNullCheck("Terraform Step configuration is null", terraformStepConfiguration);
    terraformStepConfiguration.validateParams();
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();

    if (terraformStepConfiguration.terraformStepConfigurationType == TerraformStepConfigurationType.INLINE) {
      TerraformExecutionData terraformExecutionData = terraformStepConfiguration.terraformExecutionData;

      connectorRefMap.put("configuration.spec.configFiles.store.spec.connectorRef",
          terraformExecutionData.getTerraformConfigFilesWrapper().store.getSpec().getConnectorReference());

      List<TerraformVarFileWrapper> terraformVarFiles = terraformExecutionData.getTerraformVarFiles();

      if (EmptyPredicate.isNotEmpty(terraformVarFiles)) {
        for (TerraformVarFileWrapper terraformVarFile : terraformVarFiles) {
          if (terraformVarFile.getVarFile().getType().equals(TerraformVarFileTypes.Remote)) {
            connectorRefMap.put("configuration.spec.varFiles." + terraformVarFile.getVarFile().identifier
                    + ".spec.store.spec.connectorRef",
                ((RemoteTerraformVarFileSpec) terraformVarFile.varFile.spec).store.getSpec().getConnectorReference());
          }
        }
      }
    }
    return connectorRefMap;
  }
}
