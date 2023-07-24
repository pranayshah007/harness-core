/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.services;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.favorites.entities.Favorite;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public interface FavoritesService {
  /**
   *
   * @param favoriteDTO
   * @return FavoriteEntity
   */
  Favorite createFavorite(FavoriteDTO favoriteDTO, String accountIdentifier);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @param resourceType
   * @return a list of favorite present in the scope for the matching resource type of the user
   */

  List<Favorite> getFavorites(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId, String resourceType);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @param resourceType
   * @param resourceId
   * @return a boolean whether the resource is a favorite
   */
  boolean isFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId,
      String resourceType, String resourceId);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @return a list of favorites present in the scope for the user
   */

  List<Favorite> getFavorites(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @param resourceType
   * @param resourceId
   */

  void deleteFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId,
      String resourceType, String resourceId);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param resourceType
   * @param resourceId
   */
  void deleteFavorites(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String resourceType, String resourceId);
}
