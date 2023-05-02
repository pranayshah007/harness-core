/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.FavoriteEntity;
import io.harness.favorites.services.FavoriteService;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.UserInfo;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.favorites.spring.FavoriteRepository;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class FavoriteServiceImpl implements FavoriteService {
  private final UserClient userClient;
  private final ConnectorResourceClient connectorResourceClient;
  private final PipelineServiceClient pipelineServiceClient;
  private final SecretNGManagerClient secretNGManagerClient;
  private final ProjectService projectService;
  private final FavoriteRepository favoriteRepository;

  @Inject
  public FavoriteServiceImpl(@Named("PRIVILEGED") UserClient userClient,
      ConnectorResourceClient connectorResourceClient, PipelineServiceClient pipelineServiceClient,
      @Named("PRIVILEGED") SecretNGManagerClient secretNGManagerClient, ProjectService projectService,
      FavoriteRepository favoriteRepository) {
    this.userClient = userClient;
    this.connectorResourceClient = connectorResourceClient;
    this.pipelineServiceClient = pipelineServiceClient;
    this.secretNGManagerClient = secretNGManagerClient;
    this.projectService = projectService;
    this.favoriteRepository = favoriteRepository;
  }
  @Override
  public FavoriteEntity createFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userId, ModuleType moduleType, ResourceType resourceType, String resourceId) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    validateFavoriteEntry(scope, userId, moduleType, resourceType, resourceId);
    try {
      FavoriteEntity favorite = FavoriteEntity.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .userIdentifier(userId)
                                    .module(moduleType)
                                    .resourceType(resourceType)
                                    .resourceIdentifier(resourceId)
                                    .build();
      return favoriteRepository.save(favorite);
    } catch (DuplicateKeyException exception) {
      throw new DuplicateKeyException("This entity is already marked as favorite");
    }
  }

  @Override
  public List<FavoriteEntity> getFavorites(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userId, ResourceType resourceType) {
    List<FavoriteEntity> favoriteEntityList =
        favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType);
    if (favoriteEntityList.isEmpty()) {
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message("No favorites found for this resource type, Please create one")
          .level(Level.ERROR)
          .reportTargets(USER)
          .build();
    }
    return favoriteEntityList;
  }

  @Override
  public List<FavoriteEntity> getFavorites(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId) {
    List<FavoriteEntity> userFavsList =
        favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, userId);
    if (userFavsList.isEmpty()) {
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message("No favorites found your user, Please create one")
          .level(Level.ERROR)
          .reportTargets(USER)
          .build();
    }
    return userFavsList;
  }

  @Override
  public boolean deleteFavorite(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId, String resourceId) {
    favoriteRepository
        .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceId);
    return true;
  }

  private void validateFavoriteEntry(
      Scope scope, String userId, ModuleType moduleType, ResourceType resourceType, String resourceId) {
    if (resourceType == null) {
      log.error("ResourceType cannot be null for favorite creation in account {}", scope.getAccountIdentifier());
      throw new InvalidRequestException("ResourceType cannot be null");
    }
    if (isEmpty(resourceId)) {
      log.error("ResourceId should be provided favorite creation in account {}", scope.getAccountIdentifier());
      throw new InvalidRequestException("ResourceId cannot be null");
    }
    Optional<UserInfo> userInfoOptional = CGRestUtils.getResponse(userClient.getUserById(userId));
    if (userInfoOptional.isEmpty()) {
      log.error("User doesn't exist, for the user {}, in account {}", userId, scope.getAccountIdentifier());
      throw new InvalidRequestException("User doesn't exist");
    }
    // Resource existence check
    boolean resourceExist = true;
    switch (resourceType) {
      case CONNECTOR:
        Optional<ConnectorDTO> connectorDTOOptional = NGRestUtils.getResponse(connectorResourceClient.get(
            resourceId, scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()));
        resourceExist = connectorDTOOptional.isPresent();
        break;
      case PIPELINE:
        PMSPipelineResponseDTO existingPipeline = NGRestUtils.getResponse(
            pipelineServiceClient.getPipelineByIdentifier(resourceId, scope.getAccountIdentifier(),
                scope.getOrgIdentifier(), scope.getProjectIdentifier(), null, null, null));
        if (existingPipeline == null || isEmpty(existingPipeline.getYamlPipeline())) {
          resourceExist = false;
        }
        break;
      case SECRET:
        SecretResponseWrapper response = NGRestUtils.getResponse(secretNGManagerClient.getSecret(
            resourceId, scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()));
        if (response == null) {
          resourceExist = false;
        }
        break;
      case PROJECT:
        Optional<Project> project =
            projectService.get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), resourceId);
        resourceExist = project.isPresent();
        break;
      default:
        break;
    }
    if (!resourceExist) {
      log.error("The Resource with ID {} and type {}, which is being marked as favorite does not exist in account {}",
          resourceId, resourceType.toString(), scope.getAccountIdentifier());
      throw new InvalidRequestException("The resource does not exist");
    }
  }
}
