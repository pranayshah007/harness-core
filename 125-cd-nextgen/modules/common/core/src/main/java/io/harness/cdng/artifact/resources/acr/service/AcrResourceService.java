/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.acr.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRequestDTO;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;

@OwnedBy(HarnessTeam.CDP)
public interface AcrResourceService {
  AcrRegistriesDTO getRegistries(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String subscriptionId);

  AcrRepositoriesDTO getRepositories(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String registry);

  AcrResponseDTO getBuildDetails(IdentifierRef connectorRef, String subscription, String registry, String repository,
      String orgIdentifier, String projectIdentifier);

  AcrBuildDetailsDTO getLastSuccessfulBuild(IdentifierRef connectorRef, String subscription, String registry,
      String repository, String orgIdentifier, String projectIdentifier, AcrRequestDTO acrRequestDTO);
}
