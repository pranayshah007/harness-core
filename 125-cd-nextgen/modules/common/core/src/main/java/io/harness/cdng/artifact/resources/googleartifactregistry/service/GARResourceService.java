/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.googleartifactregistry.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public interface GARResourceService {
  GARResponseDTO getBuildDetails(IdentifierRef GoogleArtifactRegistryRef, String region, String repositoryName,
      String project, String pkg, String version, String versionRegex, String orgIdentifier, String projectIdentifier);
  GARBuildDetailsDTO getLastSuccessfulBuild(IdentifierRef googleArtifactRegistryRef, String region,
      String repositoryName, String project, String pkg, GarRequestDTO garRequestDTO, String orgIdentifier,
      String projectIdentifier);
}
