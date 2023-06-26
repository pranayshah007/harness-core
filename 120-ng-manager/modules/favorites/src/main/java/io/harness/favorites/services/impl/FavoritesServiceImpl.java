/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.services.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.FavoritesService;
import io.harness.favorites.utils.FavoritesResourceUtils;
import io.harness.favorites.utils.FavoritesValidator;
import io.harness.repositories.favorites.spring.FavoriteRepository;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class FavoritesServiceImpl implements FavoritesService {
  private final FavoriteRepository favoriteRepository;
  private final FavoritesResourceUtils favoritesResourceUtils;
  private final FavoritesValidator favoritesValidator;
  private static final String INVALID_RESOURCE_TYPE_ERROR_MESSAGE = "Please provide a valid resource Type";

  @Inject
  public FavoritesServiceImpl(FavoriteRepository favoriteRepository, FavoritesResourceUtils favoritesResourceUtils,
      FavoritesValidator favoritesValidator) {
    this.favoriteRepository = favoriteRepository;
    this.favoritesResourceUtils = favoritesResourceUtils;
    this.favoritesValidator = favoritesValidator;
  }
  @Override
  public Favorite createFavorite(FavoriteDTO favoriteDTO, String accountIdentifier) {
    favoritesValidator.validateFavoriteEntry(favoriteDTO, accountIdentifier);
    Favorite favorite = favoritesResourceUtils.toFavoriteEntity(favoriteDTO, accountIdentifier);
    try {
      return favoriteRepository.save(favorite);
    } catch (DuplicateKeyException exception) {
      log.warn("This entity is already marked as favorite");
      return favorite;
    }
  }

  @Override
  public List<Favorite> getFavorites(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId, String resourceType) {
    ResourceType resourceTypeResolved =
        resourceType != null ? EnumUtils.getEnum(ResourceType.class, resourceType) : null;
    if (resourceType == null) {
      throw new InvalidRequestException(INVALID_RESOURCE_TYPE_ERROR_MESSAGE);
    }
    return favoriteRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceTypeResolved);
  }

  @Override
  public boolean isFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId,
      String resourceType, String resourceId) {
    ResourceType resourceTypeResolved =
        resourceType != null ? EnumUtils.getEnum(ResourceType.class, resourceType) : null;
    if (resourceType == null) {
      throw new InvalidRequestException(INVALID_RESOURCE_TYPE_ERROR_MESSAGE);
    }
    return favoriteRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceTypeResolved, resourceId)
        .isPresent();
  }

  @Override
  public List<Favorite> getFavorites(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId) {
    return favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, userId);
  }

  @Override
  public void deleteFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId,
      String resourceType, String resourceId) throws InvalidRequestException {
    ResourceType resourceTypeResolved =
        resourceType != null ? EnumUtils.getEnum(ResourceType.class, resourceType) : null;
    if (resourceTypeResolved == null) {
      throw new InvalidRequestException(INVALID_RESOURCE_TYPE_ERROR_MESSAGE);
    }
    favoriteRepository
        .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceTypeResolved, resourceId);
  }

  @Override
  public void deleteFavorites(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String resourceType, String resourceId) {
    ResourceType resourceTypeResolved =
        resourceType != null ? EnumUtils.getEnum(ResourceType.class, resourceType) : null;
    if (resourceTypeResolved == null) {
      throw new InvalidRequestException(INVALID_RESOURCE_TYPE_ERROR_MESSAGE);
    }
    favoriteRepository
        .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndResourceTypeAndResourceIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, resourceTypeResolved, resourceId);
  }
}
