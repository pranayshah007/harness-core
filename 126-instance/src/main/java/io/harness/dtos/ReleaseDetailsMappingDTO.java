/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dtos;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.releasedetailsinfo.ReleaseDetailsDTO;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class ReleaseDetailsMappingDTO {
  @NonNull private String accountIdentifier;
  @NonNull private String orgIdentifier;
  @NonNull private String projectIdentifier;
  @NonNull private String releaseKey;
  @NonNull private String infraKey;
  @NonNull private ReleaseDetailsDTO releaseDetailsDTO;
}
