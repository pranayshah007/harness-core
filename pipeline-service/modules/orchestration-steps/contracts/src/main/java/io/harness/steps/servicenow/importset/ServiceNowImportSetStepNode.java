/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.importset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.SERVICENOW_IMPORT_SET)
@OwnedBy(PIPELINE)
public class ServiceNowImportSetStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.ServiceNowImportSet;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  ServiceNowImportSetStepInfo serviceNowImportSetStepInfo;
  @Override
  public String getType() {
    return StepSpecTypeConstants.SERVICENOW_IMPORT_SET;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return serviceNowImportSetStepInfo;
  }

  enum StepType {
    ServiceNowImportSet(StepSpecTypeConstants.SERVICENOW_IMPORT_SET);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
