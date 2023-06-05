/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.utils;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.exception.InvalidRequestException;
import io.harness.favorites.ResourceType;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.UserInfo;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class FavoritesValidator {
  private final UserClient userClient;
  private final ConnectorService connectorService;
  private final PipelineServiceClient pipelineServiceClient;
  private final SecretCrudService secretService;
  private final ProjectService projectService;

  @Inject
  public FavoritesValidator(UserClient userClient, @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      PipelineServiceClient pipelineServiceClient, SecretCrudService secretService, ProjectService projectService) {
    this.userClient = userClient;
    this.connectorService = connectorService;
    this.pipelineServiceClient = pipelineServiceClient;
    this.secretService = secretService;
    this.projectService = projectService;
  }

  private void checkIfUserExist(FavoriteDTO favoriteDTO, String accountIdentifier) {
    Optional<UserInfo> userInfoOptional = CGRestUtils.getResponse(userClient.getUserById(favoriteDTO.getUserId()));
    if (userInfoOptional.isEmpty()) {
      log.error("User doesn't exist, for the user {}, in account {}", favoriteDTO.getUserId(), accountIdentifier);
      throw new InvalidRequestException("User doesn't exist");
    }
  }

  private boolean doesConnectorExist(FavoriteDTO favoriteDTO, String accountIdentifier) {
    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(
        accountIdentifier, favoriteDTO.getOrg(), favoriteDTO.getProject(), favoriteDTO.getResourceId());
    return connectorResponseDTO.isPresent();
  }

  private boolean doesPipelineExist(FavoriteDTO favoriteDTO, String accountIdentifier) {
    PMSPipelineResponseDTO existingPipeline =
        NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(favoriteDTO.getResourceId(),
            accountIdentifier, favoriteDTO.getOrg(), favoriteDTO.getProject(), null, null, null));
    if (existingPipeline == null || isEmpty(existingPipeline.getYamlPipeline())) {
      return false;
    }
    return true;
  }

  private boolean doesSecretExist(FavoriteDTO favoriteDTO, String accountIdentifier) {
    Optional<SecretResponseWrapper> secretResponse = secretService.get(
        accountIdentifier, favoriteDTO.getOrg(), favoriteDTO.getProject(), favoriteDTO.getResourceId());
    return secretResponse.isPresent();
  }

  private boolean doesProjectExit(FavoriteDTO favoriteDTO, String accountIdentifier) {
    Optional<Project> project =
        projectService.get(accountIdentifier, favoriteDTO.getOrg(), favoriteDTO.getResourceId());
    return project.isPresent();
  }

  public void validateFavoriteEntry(FavoriteDTO favoriteDTO, String accountIdentifier) {
    checkIfUserExist(favoriteDTO, accountIdentifier);
    // Resource existence check
    boolean resourceExist = true;
    switch (EnumUtils.getEnum(ResourceType.class, favoriteDTO.getResourceType().toString())) {
      case CONNECTOR:
        resourceExist = doesConnectorExist(favoriteDTO, accountIdentifier);
        break;
      case PIPELINE:
        resourceExist = doesPipelineExist(favoriteDTO, accountIdentifier);
        break;
      case SECRET:
        resourceExist = doesSecretExist(favoriteDTO, accountIdentifier);
        break;
      case PROJECT:
        resourceExist = doesProjectExit(favoriteDTO, accountIdentifier);
        break;
      case DELEGATE:
        break;
      default:
        throw new InvalidRequestException("Please check the resource type provided");
    }
    if (!resourceExist) {
      log.error("The Resource with ID {} and type {}, which is being marked as favorite does not exist in account {}",
          favoriteDTO.getResourceId(), favoriteDTO.getResourceType(), accountIdentifier);
      throw new InvalidRequestException("The resource does not exist");
    }
  }
}
