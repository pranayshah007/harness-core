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
@TypeAlias("terragruntApplyStepInfo")
@JsonTypeName(StepSpecTypeConstants.TERRAGRUNT_APPLY)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntApplyStepInfo")
public class TerragruntApplyStepInfo
    extends TerragruntApplyBaseStepInfo implements CDStepInfo, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @JsonProperty("configuration") TerragruntStepConfiguration terragruntStepConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public TerragruntApplyStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      TerragruntStepConfiguration terragruntStepConfiguration) {
    super(provisionerIdentifier, delegateSelectors);
    this.terragruntStepConfiguration = terragruntStepConfiguration;
  }

  @Override
  public StepType getStepType() {
    return TerragruntApplyStep.STEP_TYPE;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParams();
    return TerragruntApplyStepParameters.infoBuilder()
        .provisionerIdentifier(getProvisionerIdentifier())
        .configuration(terragruntStepConfiguration.toStepParameters())
        .delegateSelectors(getDelegateSelectors())
        .build();
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    TerragruntStepHelper.addConnectorRef(connectorRefMap, terragruntStepConfiguration);
    return connectorRefMap;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  void validateSpecParams() {
    Validator.notNullCheck("Terragrunt Step configuration is null", terragruntStepConfiguration);
    terragruntStepConfiguration.validateParams();
  }
}
