/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.docker.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;

public interface DockerResourceService {
  DockerResponseDTO getBuildDetails(
      IdentifierRef dockerConnectorRef, String imagePath, String orgIdentifier, String projectIdentifier);

  DockerResponseDTO getLabels(IdentifierRef dockerConnectorRef, String imagePath, DockerRequestDTO dockerRequestDTO,
      String orgIdentifier, String projectIdentifier);

  DockerBuildDetailsDTO getSuccessfulBuild(IdentifierRef dockerConnectorRef, String imagePath,
      DockerRequestDTO dockerRequestDTO, String orgIdentifier, String projectIdentifier);

  boolean validateArtifactServer(IdentifierRef dockerConnectorRef, String orgIdentifier, String projectIdentifier);

  // In case of docker source here is docker image.
  boolean validateArtifactSource(
      String imagePath, IdentifierRef dockerConnectorRef, String orgIdentifier, String projectIdentifier);
}
