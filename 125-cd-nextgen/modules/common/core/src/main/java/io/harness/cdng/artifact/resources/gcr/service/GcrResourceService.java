/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.gcr.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrRequestDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrResponseDTO;

public interface GcrResourceService {
  GcrResponseDTO getBuildDetails(IdentifierRef gcrConnectorRef, String imagePath, String registryHostname,
      String orgIdentifier, String projectIdentifier);

  GcrBuildDetailsDTO getSuccessfulBuild(IdentifierRef dockerConnectorRef, String imagePath,
      GcrRequestDTO dockerRequestDTO, String orgIdentifier, String projectIdentifier);

  boolean validateArtifactServer(IdentifierRef gcrConnectorRef, String imagePath, String orgIdentifier,
      String projectIdentifier, String registryHostname);

  boolean validateArtifactSource(String imagePath, IdentifierRef gcrConnectorRef, String registryHostname,
      String orgIdentifier, String projectIdentifier);
}
