/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.releasedetailsinfo.ReleaseDetailsDTO;
import io.harness.entities.releasedetailsinfo.ReleaseDetails;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@UtilityClass
public class ReleaseDetailsMapper {
  public ReleaseDetailsDTO toDTO(ReleaseDetails releaseDetails) {
    return ReleaseDetailsDTO.builder()
        .serviceDetails(releaseDetails.getServiceDetails())
        .envDetails(releaseDetails.getEnvDetails())
        .build();
  }

  public ReleaseDetails toEntity(ReleaseDetailsDTO releaseDetailsDTO) {
    return ReleaseDetails.builder()
        .envDetails(releaseDetailsDTO.getEnvDetails())
        .serviceDetails(releaseDetailsDTO.getServiceDetails())
        .build();
  }
}
