/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoApiAccess;
import io.harness.connector.entities.embedded.githubconnector.GithubApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabApiAccess;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.gitsync.common.beans.AzureRepoSCM.AzureRepoSCMKeys;
import io.harness.gitsync.common.beans.GithubSCM.GithubSCMKeys;
import io.harness.gitsync.common.beans.GitlabSCM.GitlabSCMKeys;
import io.harness.gitsync.common.beans.UserSourceCodeManager;
import io.harness.gitsync.common.dtos.AzureRepoSCMDTO;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.GitlabSCMDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.mappers.AzureRepoSCMMapper;
import io.harness.gitsync.common.mappers.GithubSCMMapper;
import io.harness.gitsync.common.mappers.GitlabSCMMapper;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.ng.userprofile.commons.SCMType;

import com.google.inject.Inject;
import java.util.Map;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class SCMMapperHelper {
  @Inject private Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;

  public UserSourceCodeManagerDTO toDTO(UserSourceCodeManager userSourceCodeManager) {
    return scmMapBinder.get(userSourceCodeManager.getType()).toDTO(userSourceCodeManager);
  }

  public UserSourceCodeManager toEntity(UserSourceCodeManagerDTO userSourceCodeManagerDTO) {
    return scmMapBinder.get(userSourceCodeManagerDTO.getType()).toEntity(userSourceCodeManagerDTO);
  }

  public Update getUpdateOperationForApiAccess(UserSourceCodeManagerDTO userSourceCodeManagerDTO) {
    Update updateOperation = new Update();
    switch (userSourceCodeManagerDTO.getType()) {
      case GITHUB:
        GithubApiAccess githubApiAccess =
            ((GithubSCMMapper) scmMapBinder.get(userSourceCodeManagerDTO.getType()))
                .toApiAccess(((GithubSCMDTO) userSourceCodeManagerDTO).getApiAccess().getSpec(),
                    ((GithubSCMDTO) userSourceCodeManagerDTO).getApiAccess().getType());
        updateOperation.set(GithubSCMKeys.githubApiAccess, githubApiAccess);
        break;
      case GITLAB:
        GitlabApiAccess gitlabApiAccess =
            ((GitlabSCMMapper) scmMapBinder.get(userSourceCodeManagerDTO.getType()))
                .toApiAccess(((GitlabSCMDTO) userSourceCodeManagerDTO).getApiAccess().getSpec(),
                    ((GitlabSCMDTO) userSourceCodeManagerDTO).getApiAccess().getType());
        updateOperation.set(GitlabSCMKeys.gitlabApiAccess, gitlabApiAccess);
        break;
      case AZURE_REPO:
        AzureRepoApiAccess azureRepoApiAccess =
            ((AzureRepoSCMMapper) scmMapBinder.get(userSourceCodeManagerDTO.getType()))
                .toApiAccess(((AzureRepoSCMDTO) userSourceCodeManagerDTO).getApiAccess().getSpec(),
                    ((AzureRepoSCMDTO) userSourceCodeManagerDTO).getApiAccess().getType());
        updateOperation.set(AzureRepoSCMKeys.azureRepoApiAccess, azureRepoApiAccess);
        break;
      default:
        throw new UnknownEnumTypeException("SCM type not supported ", userSourceCodeManagerDTO.getType().name());
    }
    return updateOperation;
  }
}
