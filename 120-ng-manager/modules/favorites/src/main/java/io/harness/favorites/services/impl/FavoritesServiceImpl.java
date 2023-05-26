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
  public List<Favorite> getFavorites(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userId, ResourceType resourceType) {
    return favoriteRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceType(
            accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceType);
  }

  @Override
  public List<Favorite> getFavorites(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId) {
    return favoriteRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, userId);
  }

  @Override
  public void deleteFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId,
      String resourceType, String resourceId) {
    ResourceType resourceTypeEnum = EnumUtils.getEnum(ResourceType.class, resourceType);
    if (resourceTypeEnum != null) {
      favoriteRepository
          .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndUserIdentifierAndResourceTypeAndResourceIdentifier(
              accountIdentifier, orgIdentifier, projectIdentifier, userId, resourceTypeEnum, resourceId);
    } else {
      throw new InvalidRequestException("Please provide a valid resource Type");
    }
  }
}
