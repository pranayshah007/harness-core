/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.healthsource;

import io.harness.cvng.core.entities.HealthSourceParams;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthSourceParamsDTO {
  String region;

  public static HealthSourceParamsDTO getHealthSourceParamsDTO(HealthSourceParams healthSourceParams) {
    return HealthSourceParamsDTO.builder()
        .region(Optional.ofNullable(healthSourceParams).orElse(HealthSourceParams.builder().build()).getRegion())
        .build();
  }

  @JsonIgnore
  public HealthSourceParams getHealthSourceParamsEntity() {
    return HealthSourceParams.builder().region(region).build();
  }
}