/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.resourceconstraint;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.utils.PmsConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.resourcerestraint.QueueStep;
import io.harness.steps.resourcerestraint.ResourceRestraintFacilitator;
import io.harness.steps.resourcerestraint.ResourceRestraintSpecParameters;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.QueueHoldingScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.QUEUE)
@TypeAlias("queueStepInfo")
@RecasterAlias("io.harness.plancreator.steps.resourceconstraint.QueueStepInfo")
@OwnedBy(HarnessTeam.PIPELINE)
public class QueueStepInfo implements PMSStepInfo {
  // HIDDEN FIELD TO AVOID ISSUES WHEN REFLECTION RUNS TO EXTRACT FIELDS TO ADD INTO VARIABLE EXPRESSIONS
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> key;
  @NotNull
  @ApiModelProperty(dataType = "io.harness.steps.resourcerestraint.beans.QueueHoldingScope")
  QueueHoldingScope scope;

  @JsonIgnore
  @Override
  public StepType getStepType() {
    return QueueStep.STEP_TYPE;
  }

  @JsonIgnore
  @Override
  public String getFacilitatorType() {
    return ResourceRestraintFacilitator.FACILITATOR_TYPE.getType();
  }

  /**
   * Create the parameters using a fixed name, permits, and acquire mode.
   *
   * <ul>
   *     <li>name: {@link PmsConstants#QUEUING_RC_NAME}</li>
   *     <li>permits: {@link PmsConstants#QUEUING_RC_PERMITS}</li>
   *     <li>acquireMode: {@link AcquireMode#ENSURE} (FIFO)</li>
   * </ul>
   */
  @Override
  public SpecParameters getSpecParameters() {
    return ResourceRestraintSpecParameters.builder()
        .resourceUnit(key)
        .holdingScope(scope.getHoldingScope())
        .name(PmsConstants.QUEUING_RC_NAME)
        .permits(PmsConstants.QUEUING_RC_PERMITS)
        .acquireMode(AcquireMode.ENSURE)
        .build();
  }
}
