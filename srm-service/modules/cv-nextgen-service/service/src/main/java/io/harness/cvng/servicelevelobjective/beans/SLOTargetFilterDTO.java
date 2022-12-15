/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.cvng.CVConstants.SLO_TARGET_TYPE;

import io.harness.cvng.servicelevelobjective.beans.slotargetspec.SLOTargetSpec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SLOTargetFilterDTO {
  @JsonProperty(SLO_TARGET_TYPE) SLOTargetType type;
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = SLO_TARGET_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true)
  @Valid
  @NotNull
  SLOTargetSpec spec;

  public SLOTargetType getType() {
    return this.spec.getType();
  }
}
