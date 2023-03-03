/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceLevelIndicatorDTO {
  String name;
  String identifier;
  @NotNull ServiceLevelIndicatorSpec spec;
  @Deprecated SLIMissingDataType sliMissingDataType;
  @JsonIgnore
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  String healthSourceRef; // TODO: we need to move health source ref to this level.
}
