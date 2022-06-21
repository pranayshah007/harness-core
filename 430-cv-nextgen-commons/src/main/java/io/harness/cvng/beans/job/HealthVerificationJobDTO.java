/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.job;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@JsonTypeName("HEALTH")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class HealthVerificationJobDTO extends VerificationJobDTO {
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.HEALTH;
  }
}
