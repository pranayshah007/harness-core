/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.nexus.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;

import software.wings.helpers.ext.nexus.NexusRepositories;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDP)
public interface NexusResourceService {
  NexusResponseDTO getBuildDetails(IdentifierRef nexusConnectorRef, String repositoryName, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String orgIdentifier,
      String projectIdentifier, String groupId, String artifactId, String extension, String classifier,
      String packageName, String group);

  NexusResponseDTO getBuildDetails(IdentifierRef nexusConnectorRef, String repositoryName, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String orgIdentifier,
      String projectIdentifier);

  NexusBuildDetailsDTO getSuccessfulBuild(IdentifierRef nexusConnectorRef, String repositoryName, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, NexusRequestDTO nexusRequestDTO,
      String orgIdentifier, String projectIdentifier);

  boolean validateArtifactServer(IdentifierRef nexusConnectorRef, String orgIdentifier, String projectIdentifier);
  List<NexusRepositories> getRepositories(
      IdentifierRef nexusConnectorRef, String orgIdentifier, String projectIdentifier, String repositoryFormat);

  List<String> getGroupIds(String accountId, String orgIdentifier, String projectIdentifier, IdentifierRef connectorRef,
      String repositoryFormat, String repository, ArtifactSourceType artifactSourceType);

  List<String> getArtifactIds(String accountId, String orgIdentifier, String projectIdentifier,
      IdentifierRef connectorRef, String repositoryFormat, String repository, String groupId,
      ArtifactSourceType artifactSourceType);
}
