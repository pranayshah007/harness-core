/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.Validator;

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
@JsonTypeName(StepSpecTypeConstants.TERRAGRUNT_PLAN)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntPlanStepInfo")
public class TerragruntPlanStepInfo extends TerragruntPlanBaseStepInfo implements CDStepInfo, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("configuration") TerragruntPlanExecutionData terragruntPlanExecutionData;

  @Builder(builderMethodName = "infoBuilder")
  public TerragruntPlanStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      TerragruntPlanExecutionData terragruntPlanExecutionData) {
    super(provisionerIdentifier, delegateSelectors);
    this.terragruntPlanExecutionData = terragruntPlanExecutionData;
  }

  @Override
  public StepType getStepType() {
    return TerragruntPlanStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParams();
    return TerragruntPlanStepParameters.infoBuilder()
        .provisionerIdentifier(provisionerIdentifier)
        .delegateSelectors(delegateSelectors)
        .configuration(terragruntPlanExecutionData.toStepParameters())
        .build();
  }

  void validateSpecParams() {
    Validator.notNullCheck("Terragrunt Plan configuration is NULL", terragruntPlanExecutionData);
    terragruntPlanExecutionData.validateParams();
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    validateSpecParams();
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put("configuration.configFiles.store.spec.connectorRef",
        terragruntPlanExecutionData.getTerragruntConfigFilesWrapper().store.getSpec().getConnectorReference());

    List<TerragruntVarFileWrapper> terragruntVarFiles = terragruntPlanExecutionData.getTerragruntVarFiles();
    TerragruntStepHelper.addConnectorRefFromVarFiles(terragruntVarFiles, connectorRefMap);

    TerragruntBackendConfig terragruntBackendConfig = terragruntPlanExecutionData.getTerragruntBackendConfig();
    TerragruntStepHelper.addConnectorRefFromBackendConfig(terragruntBackendConfig, connectorRefMap);

    connectorRefMap.put("configuration.secretManagerRef", terragruntPlanExecutionData.getSecretManagerRef());
    return connectorRefMap;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
