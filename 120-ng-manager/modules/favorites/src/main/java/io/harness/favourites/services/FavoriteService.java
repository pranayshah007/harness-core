/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favourites.services;

import io.harness.favourites.ResourceType;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;

import java.util.List;

public interface FavoriteService {
  FavoriteResponse createFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userId, ResourceType resourceType, String resourceId);

  List<FavoriteResponse> getFavorite(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userId, ResourceType resourceType);

  List<FavoriteResponse> getAllFavoritesOfUser(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId);

  boolean deleteFavorite(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String userId, String resourceId);
}
