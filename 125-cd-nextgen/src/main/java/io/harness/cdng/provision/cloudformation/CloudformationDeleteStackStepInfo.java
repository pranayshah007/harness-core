/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

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
import io.harness.validation.Validator;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("cloudformationDeleteStackStepInfo")
@JsonTypeName(StepSpecTypeConstants.CLOUDFORMATION_DELETE_STACK)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepInfo")
public class CloudformationDeleteStackStepInfo
    extends CloudformationDeleteStackBaseStepInfo implements CDStepInfo, Visitable, WithConnectorRef {
  @NotNull @JsonProperty("configuration") CloudformationDeleteStackStepConfiguration cloudformationStepConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public CloudformationDeleteStackStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelector,
      CloudformationDeleteStackStepConfiguration cloudformationStepConfiguration, String uuid) {
    super(delegateSelector, uuid);
    this.cloudformationStepConfiguration = cloudformationStepConfiguration;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    validateSpecParameters();
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    if (cloudformationStepConfiguration.getType().equals(CloudformationDeleteStackStepConfigurationTypes.Inline)) {
      connectorRefMap.put("configuration.spec.connectorRef",
          ((InlineCloudformationDeleteStackStepConfiguration) cloudformationStepConfiguration.getSpec())
              .getConnectorRef());
    }
    return connectorRefMap;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return CloudformationDeleteStackStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParameters();
    return CloudformationDeleteStackStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .configuration(cloudformationStepConfiguration)
        .build();
  }

  void validateSpecParameters() {
    Validator.notNullCheck("CloudformationStepConfiguration is null", cloudformationStepConfiguration);
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
