/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabOauth;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.gitsync.common.beans.GitlabSCM;
import io.harness.gitsync.common.dtos.GitlabSCMDTO;
import io.harness.gitsync.common.dtos.GitlabSCMRequestDTO;
import io.harness.gitsync.common.dtos.GitlabSCMResponseDTO;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitlabSCMMapper
    extends UserSourceCodeManagerMapper<GitlabSCMDTO, GitlabSCM, GitlabSCMRequestDTO, GitlabSCMResponseDTO> {
  @Override
  GitlabSCM toEntityInternal(GitlabSCMDTO sourceCodeManagerDTO) {
    return GitlabSCM.builder()
        .apiAccessType(sourceCodeManagerDTO.getApiAccess().getType())
        .gitlabApiAccess(
            toApiAccess(sourceCodeManagerDTO.getApiAccess().getSpec(), sourceCodeManagerDTO.getApiAccess().getType()))
        .build();
  }

  @Override
  GitlabSCMDTO toDTOInternal(GitlabSCM sourceCodeManager) {
    return GitlabSCMDTO.builder()
        .apiAccess(toApiAccessDTO(sourceCodeManager.getApiAccessType(), sourceCodeManager.getGitlabApiAccess()))
        .build();
  }

  @Override
  GitlabSCMResponseDTO toResponseDTOInternal(GitlabSCMDTO dto) {
    return GitlabSCMResponseDTO.builder().apiAccess(dto.getApiAccess()).build();
  }
  @Override
  GitlabSCMDTO toServiceDTOInternal(GitlabSCMRequestDTO userSourceCodeManagerRequestDTO) {
    return GitlabSCMDTO.builder()
        .apiAccess(userSourceCodeManagerRequestDTO.getAuthentication().getApiAccessDTO())
        .build();
  }

  GitlabApiAccess toApiAccess(GitlabApiAccessSpecDTO spec, GitlabApiAccessType apiAccessType) {
    switch (apiAccessType) {
      case OAUTH:
        final GitlabOauthDTO oauthDTO = (GitlabOauthDTO) spec;
        return GitlabOauth.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(oauthDTO.getTokenRef()))
            .refreshTokenRef(SecretRefHelper.getSecretConfigString(oauthDTO.getRefreshTokenRef()))
            .build();
      default:
        throw new UnknownEnumTypeException("Azure Repo Api Access Type", apiAccessType.getDisplayName());
    }
  }

  public static GitlabApiAccessDTO toApiAccessDTO(GitlabApiAccessType apiAccessType, GitlabApiAccess gitlabApiAccess) {
    GitlabApiAccessSpecDTO apiAccessSpecDTO = null;
    switch (apiAccessType) {
      case OAUTH:
        final GitlabOauth gitlabOauth = (GitlabOauth) gitlabApiAccess;
        apiAccessSpecDTO = GitlabOauthDTO.builder()
                               .tokenRef(SecretRefHelper.createSecretRef(gitlabOauth.getTokenRef()))
                               .refreshTokenRef(SecretRefHelper.createSecretRef(gitlabOauth.getRefreshTokenRef()))
                               .build();
        break;
      default:
        throw new UnknownEnumTypeException("Azure Repo Api Access Type", apiAccessType.getDisplayName());
    }
    return GitlabApiAccessDTO.builder().type(apiAccessType).spec(apiAccessSpecDTO).build();
  }
}