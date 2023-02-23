/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import static io.harness.cvng.CVConstants.DOWNTIME_SPEC_TYPE;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "DowntimeSpecKeys")
@EqualsAndHashCode()
public class DowntimeSpecDTO {
  @JsonProperty(DOWNTIME_SPEC_TYPE) DowntimeType type;
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = DOWNTIME_SPEC_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      visible = true)
  @Valid
  @NotNull
  DowntimeSpec spec;

  public DowntimeType getType() {
    return this.spec.getType();
  }
}
