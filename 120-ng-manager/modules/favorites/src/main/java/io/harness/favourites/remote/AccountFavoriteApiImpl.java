/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favourites.remote;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.favourites.ResourceType;
import io.harness.favourites.services.FavoriteService;
import io.harness.spec.server.ng.v1.AccountFavoritesApi;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AccountFavoriteApiImpl implements AccountFavoritesApi {
  FavoriteService favoriteService;
  @Override
  public Response createAccountFavorite(
      @NotNull String userId, String harnessAccount, String resourceType, String resourceId) {
    return Response.status(Response.Status.CREATED)
        .entity(favoriteService.createFavorite(
            harnessAccount, null, null, userId, ResourceType.valueOf(resourceType), resourceId))
        .build();
  }

  @Override
  public Response deleteAccountFavorite(String userId, String harnessAccount, String resourceId) {
    favoriteService.deleteFavorite(harnessAccount, null, null, userId, resourceId);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getAccountFavorite(String userId, String harnessAccount, String resourceType) {
    if (isNotEmpty(resourceType)) {
      return Response.ok()
          .entity(favoriteService.getFavorite(harnessAccount, null, null, userId, ResourceType.valueOf(resourceType)))
          .build();
    }
    return Response.ok().entity(favoriteService.getAllFavoritesOfUser(harnessAccount, null, null, userId)).build();
  }
}
