/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.ecr.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrListImagesDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrResponseDTO;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(PIPELINE)
public interface EcrResourceService {
  EcrResponseDTO getBuildDetails(IdentifierRef ecrConnectorRef, String registryId, String imagePath, String region,
      String orgIdentifier, String projectIdentifier);

  EcrBuildDetailsDTO getSuccessfulBuild(IdentifierRef dockerConnectorRef, String registryId, String imagePath,
      EcrRequestDTO dockerRequestDTO, String orgIdentifier, String projectIdentifier);

  boolean validateArtifactServer(IdentifierRef ecrConnectorRef, String registryId, String imagePath,
      String orgIdentifier, String projectIdentifier, String region);

  boolean validateArtifactSource(String registryId, String imagePath, IdentifierRef ecrConnectorRef, String region,
      String orgIdentifier, String projectIdentifier);

  EcrListImagesDTO getImages(
      IdentifierRef ecrConnectorRef, String registryId, String region, String orgIdentifier, String projectIdentifier);
}
