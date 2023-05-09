/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceLevelObjectiveDetailsDTO {
  @NotNull String accountId;
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;
  @NotNull @NotBlank String serviceLevelObjectiveRef;
  @NotNull @Min(0) @Max(100) Double weightagePercentage;
}
