/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slotargetspec;

import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RollingSLOTargetSpec extends SLOTargetSpec {
  @NotNull String periodLength;

  @Override
  public SLOTargetType getType() {
    return SLOTargetType.ROLLING;
  }
}
