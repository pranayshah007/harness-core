/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.services;

import io.harness.ModuleType;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.FavoriteEntity;

import java.util.List;

public interface FavoriteService {
  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @param moduleType
   * @param resourceType
   * @param resourceId
   * @return the favoriteEntity which was created
   */
  FavoriteEntity createFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId,
      ModuleType moduleType, ResourceType resourceType, String resourceId);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @param resourceType
   * @return a singleton list of favorite present in the scope for the matching resource type of the user
   */

  List<FavoriteEntity> getFavorites(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userId, ResourceType resourceType);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @return a list of favorites present in the scope for the user
   */

  List<FavoriteEntity> getFavorites(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId);

  /**
   *
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param userId
   * @param resourceId
   * @return
   */

  boolean deleteFavorite(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId, String resourceId);
}
