/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.githubconnector.GithubApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubOauth;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.gitsync.common.beans.GithubSCM;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.GithubSCMRequestDTO;
import io.harness.gitsync.common.dtos.GithubSCMResponseDTO;

@OwnedBy(HarnessTeam.PIPELINE)
public class GithubSCMMapper
    extends UserSourceCodeManagerMapper<GithubSCMDTO, GithubSCM, GithubSCMRequestDTO, GithubSCMResponseDTO> {
  @Override
  GithubSCM toEntityInternal(GithubSCMDTO sourceCodeManagerDTO) {
    return GithubSCM.builder()
        .apiAccessType(sourceCodeManagerDTO.getApiAccess().getType())
        .githubApiAccess(
            toApiAccess(sourceCodeManagerDTO.getApiAccess().getSpec(), sourceCodeManagerDTO.getApiAccess().getType()))
        .build();
  }

  @Override
  GithubSCMDTO toDTOInternal(GithubSCM sourceCodeManager) {
    return GithubSCMDTO.builder()
        .apiAccess(toApiAccessDTO(sourceCodeManager.getApiAccessType(), sourceCodeManager.getGithubApiAccess()))
        .build();
  }

  @Override
  GithubSCMResponseDTO toResponseDTOInternal(GithubSCMDTO dto) {
    return GithubSCMResponseDTO.builder().apiAccess(dto.getApiAccess()).build();
  }

  @Override
  GithubSCMDTO toServiceDTOInternal(GithubSCMRequestDTO userSourceCodeManagerRequestDTO) {
    return GithubSCMDTO.builder()
        .apiAccess(userSourceCodeManagerRequestDTO.getAuthentication().getApiAccessDTO())
        .build();
  }

  public GithubApiAccess toApiAccess(GithubApiAccessSpecDTO spec, GithubApiAccessType apiAccessType) {
    switch (apiAccessType) {
      case OAUTH:
        final GithubOauthDTO GithubOauthDTO = (GithubOauthDTO) spec;
        return GithubOauth.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(GithubOauthDTO.getTokenRef()))
            .build();
      default:
        throw new UnknownEnumTypeException("Github Api Access Type", apiAccessType.getDisplayName());
    }
  }

  GithubApiAccessDTO toApiAccessDTO(GithubApiAccessType apiAccessType, GithubApiAccess githubApiAccess) {
    GithubApiAccessSpecDTO apiAccessSpecDTO = null;
    switch (apiAccessType) {
      case OAUTH:
        final GithubOauth githubOauth = (GithubOauth) githubApiAccess;
        apiAccessSpecDTO =
            GithubOauthDTO.builder().tokenRef(SecretRefHelper.createSecretRef(githubOauth.getTokenRef())).build();
        break;
      default:
        throw new UnknownEnumTypeException("Github Api Access Type", apiAccessType.getDisplayName());
    }
    return GithubApiAccessDTO.builder().type(apiAccessType).spec(apiAccessSpecDTO).build();
  }
}