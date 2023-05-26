/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.utils;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;

import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.EnumUtils;

@Singleton
@OwnedBy(HarnessTeam.PL)
public class FavoritesResourceUtils {
  private FavoriteDTO toFavoriteDTO(Favorite favorite) {
    return new FavoriteDTO()
        .org(favorite.getOrgIdentifier())
        .project(favorite.getProjectIdentifier())
        .userId(favorite.getUserIdentifier())
        .module(favorite.getModule().toString())
        .resourceType(favorite.getResourceType().toString())
        .resourceId(favorite.getResourceIdentifier());
  }

  public Favorite toFavoriteEntity(FavoriteDTO favoriteDTO, String accountIdentifier) {
    return Favorite.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(favoriteDTO.getOrg())
        .projectIdentifier(favoriteDTO.getProject())
        .userIdentifier(favoriteDTO.getUserId())
        .module(EnumUtils.getEnum(ModuleType.class, favoriteDTO.getModule()))
        .resourceType(EnumUtils.getEnum(ResourceType.class, favoriteDTO.getResourceType()))
        .resourceIdentifier(favoriteDTO.getResourceId())
        .build();
  }
  public FavoriteResponse toFavoriteResponse(Favorite favorite) {
    FavoriteResponse favoriteResponse = new FavoriteResponse();
    favoriteResponse.setFavorite(toFavoriteDTO(favorite));
    favoriteResponse.setCreated(favorite.getCreated());
    return favoriteResponse;
  }

  public List<FavoriteResponse> toFavoriteResponse(List<Favorite> favoriteList) {
    return favoriteList.stream().map(this::toFavoriteResponse).collect(Collectors.toList());
  }
}
