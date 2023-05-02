/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.favorites.entities.FavoriteEntity;
import io.harness.spec.server.ng.v1.model.Favorite;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;

import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.PL)
public class FavoritesResourceUtils {
  private Favorite toFavoriteDTO(FavoriteEntity favoriteEntity) {
    Favorite favorite = new Favorite();
    favorite.account(favoriteEntity.getAccountIdentifier());
    favorite.org(favoriteEntity.getOrgIdentifier());
    favorite.project(favoriteEntity.getProjectIdentifier());
    favorite.userId(favoriteEntity.getUserIdentifier());
    favorite.module(favoriteEntity.getModule().getDisplayName());
    favorite.resourceType(favoriteEntity.getResourceType().toString());
    favorite.resourceId(favoriteEntity.getResourceIdentifier());
    return favorite;
  }
  public FavoriteResponse toFavoriteResponse(FavoriteEntity favorite) {
    FavoriteResponse favoriteResponse = new FavoriteResponse();
    favoriteResponse.setFavorite(toFavoriteDTO(favorite));
    favoriteResponse.setCreated(favorite.getCreated());
    return favoriteResponse;
  }

  public List<FavoriteResponse> toFavoriteResponse(List<FavoriteEntity> favoriteList) {
    return favoriteList.stream().map(this::toFavoriteResponse).collect(Collectors.toList());
  }
}
