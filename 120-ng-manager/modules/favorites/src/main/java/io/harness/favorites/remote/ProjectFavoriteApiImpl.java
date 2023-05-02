/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.remote;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ModuleType;
import io.harness.favorites.FavoritesResourceUtils;
import io.harness.favorites.ResourceType;
import io.harness.favorites.services.FavoriteService;
import io.harness.spec.server.ng.v1.ProjectFavoritesApi;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ProjectFavoriteApiImpl implements ProjectFavoritesApi {
  @Inject private final FavoriteService favoriteService;
  @Inject private final FavoritesResourceUtils favoritesResourceUtils;

  @Override
  public Response createProjectFavorite(String org, String project, @NotNull String userId, @NotNull String moduleType,
      String harnessAccount, String resourceType, String resourceId) {
    FavoriteResponse favoriteResponse = favoritesResourceUtils.toFavoriteResponse(favoriteService.createFavorite(
        harnessAccount, org, project, userId, EnumUtils.getEnum(ModuleType.class, moduleType),
        EnumUtils.getEnum(ResourceType.class, resourceType), resourceId));
    return Response.status(Response.Status.CREATED).entity(favoriteResponse).build();
  }

  @Override
  public Response deleteProjectFavorite(
      String org, String project, String userId, String harnessAccount, String resourceId) {
    favoriteService.deleteFavorite(harnessAccount, org, project, userId, resourceId);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getProjectFavorite(
      String org, String project, String userId, String harnessAccount, String resourceType) {
    if (isNotEmpty(resourceType)) {
      List<FavoriteResponse> favoriteResponses = favoritesResourceUtils.toFavoriteResponse(favoriteService.getFavorites(
          harnessAccount, org, project, userId, EnumUtils.getEnum(ResourceType.class, resourceType)));
      return Response.ok().entity(favoriteResponses).build();
    }
    List<FavoriteResponse> favoriteResponses =
        favoritesResourceUtils.toFavoriteResponse(favoriteService.getFavorites(harnessAccount, null, null, userId));
    return Response.ok().entity(favoriteResponses).build();
  }
}
