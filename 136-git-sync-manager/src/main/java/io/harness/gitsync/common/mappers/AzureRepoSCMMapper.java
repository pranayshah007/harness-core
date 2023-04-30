/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoOAuth;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoOAuthDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.gitsync.common.beans.AzureRepoSCM;
import io.harness.gitsync.common.dtos.AzureRepoSCMDTO;
import io.harness.gitsync.common.dtos.AzureRepoSCMRequestDTO;
import io.harness.gitsync.common.dtos.AzureRepoSCMResponseDTO;

@OwnedBy(HarnessTeam.PIPELINE)
public class AzureRepoSCMMapper extends UserSourceCodeManagerMapper<AzureRepoSCMDTO, AzureRepoSCM,
    AzureRepoSCMRequestDTO, AzureRepoSCMResponseDTO> {
  AzureRepoSCM toEntityInternal(AzureRepoSCMDTO sourceCodeManagerDTO) {
    return AzureRepoSCM.builder()
        .apiAccessType(sourceCodeManagerDTO.getApiAccess().getType())
        .azureRepoApiAccess(
            toApiAccess(sourceCodeManagerDTO.getApiAccess().getSpec(), sourceCodeManagerDTO.getApiAccess().getType()))
        .build();
  }

  AzureRepoSCMDTO toDTOInternal(AzureRepoSCM sourceCodeManager) {
    return AzureRepoSCMDTO.builder()
        .apiAccess(toApiAccessDTO(sourceCodeManager.getApiAccessType(), sourceCodeManager.getAzureRepoApiAccess()))
        .build();
  }

  @Override
  AzureRepoSCMDTO toServiceDTOInternal(AzureRepoSCMRequestDTO userSourceCodeManagerRequestDTO) {
    return AzureRepoSCMDTO.builder()
        .apiAccess(userSourceCodeManagerRequestDTO.getAuthentication().getApiAccessDTO())
        .build();
  }

  @Override
  AzureRepoSCMResponseDTO toResponseDTOInternal(AzureRepoSCMDTO dto) {
    return AzureRepoSCMResponseDTO.builder().apiAccess(dto.getApiAccess()).build();
  }

  public AzureRepoApiAccess toApiAccess(AzureRepoApiAccessSpecDTO spec, AzureRepoApiAccessType apiAccessType) {
    switch (apiAccessType) {
      case OAUTH:
        final AzureRepoOAuthDTO azureRepoOAuth = (AzureRepoOAuthDTO) spec;
        return AzureRepoOAuth.builder()
            .refreshTokenRef(SecretRefHelper.getSecretConfigString(azureRepoOAuth.getRefreshTokenRef()))
            .tokenRef(SecretRefHelper.getSecretConfigString(azureRepoOAuth.getTokenRef()))
            .build();
      default:
        throw new UnknownEnumTypeException("Azure Repo Api Access Type ", apiAccessType.getDisplayName());
    }
  }

  public static AzureRepoApiAccessDTO toApiAccessDTO(
      AzureRepoApiAccessType apiAccessType, AzureRepoApiAccess azureRepoApiAccess) {
    AzureRepoApiAccessSpecDTO apiAccessSpecDTO = null;
    switch (apiAccessType) {
      case OAUTH:
        final AzureRepoOAuth azureRepoOAuth = (AzureRepoOAuth) azureRepoApiAccess;
        apiAccessSpecDTO = AzureRepoOAuthDTO.builder()
                               .tokenRef(SecretRefHelper.createSecretRef(azureRepoOAuth.getTokenRef()))
                               .refreshTokenRef(SecretRefHelper.createSecretRef(azureRepoOAuth.getRefreshTokenRef()))
                               .build();
        break;
      default:
        throw new UnknownEnumTypeException("Azure Repo Api Access Type ", apiAccessType.getDisplayName());
    }
    return AzureRepoApiAccessDTO.builder().type(apiAccessType).spec(apiAccessSpecDTO).build();
  }
}