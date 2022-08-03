package io.harness.cdng.artifact.resources.googleartifactregistry.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;

public interface GARResourceService {
  GARResponseDTO getBuildDetails(IdentifierRef GoogleArtifactRegistryRef, String region, String repositoryName,
      String project, String pkg, String versionRegex, String orgIdentifier, String projectIdentifier);
}
