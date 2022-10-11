/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.cvng.CVConstants.SLO_TARGET_TYPE;

import io.harness.cvng.servicelevelobjective.beans.slotargetspec.SLOTargetSpec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.validation.ValidationMethod;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "SLOTargetKeys")
public class SLOTargetDTO {
  @JsonProperty(SLO_TARGET_TYPE) SLOTargetType type;
  @NotNull Double sloTargetPercentage;
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = SLO_TARGET_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true)
  @Valid
  @NotNull
  SLOTargetSpec spec;

  @ValidationMethod(message = "slo target should be less than 100")
  @JsonIgnore
  public boolean isSloTarget() {
    return sloTargetPercentage < 100;
  }

  public SLOTargetType getType() {
    return this.spec.getType();
  }
}
