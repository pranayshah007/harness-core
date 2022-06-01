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
@TypeAlias("CloudformationRollbackStepInfo")
@JsonTypeName(StepSpecTypeConstants.CLOUDFORMATION_ROLLBACK_STACK)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.cloudformation.CloudformationRollbackStepInfo")
public class CloudformationRollbackStepInfo
    extends CloudformationRollbackBaseStepInfo implements CDStepInfo, Visitable, WithConnectorRef {
  @NotNull CloudformationRollbackStepConfiguration configuration;

  @Builder(builderMethodName = "infoBuilder")
  public CloudformationRollbackStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      CloudformationRollbackStepConfiguration cloudformationStepConfiguration) {
    super(delegateSelectors);
    this.configuration = cloudformationStepConfiguration;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    return new HashMap<>();
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return CloudformationRollbackStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParameters();
    return CloudformationRollbackStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .configuration(configuration)
        .build();
  }

  private void validateSpecParameters() {
    Validator.notNullCheck("Cloudformation Rollback configuration is null", configuration);
    configuration.validateParams();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
