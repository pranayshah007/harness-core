/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.remote;

import static java.util.Objects.nonNull;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.favorites.services.FavoritesService;
import io.harness.favorites.utils.FavoritesResourceUtils;
import io.harness.spec.server.ng.v1.OrgFavoritesApi;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class OrgFavoritesApiImpl implements OrgFavoritesApi {
  @Inject private final FavoritesService favoritesService;
  @Inject private final FavoritesResourceUtils favoritesResourceUtils;
  @Override
  public Response createOrgFavorite(String org, @Valid FavoriteDTO body, String accountIdentifier) {
    if (!Objects.equals(org, body.getOrg()) || nonNull(body.getProject())) {
      throw new InvalidRequestException(
          "Org scoped request is having different project in payload OR non null project");
    }
    FavoriteResponse favoriteResponse =
        favoritesResourceUtils.toFavoriteResponse(favoritesService.createFavorite(body, accountIdentifier));
    return Response.status(Response.Status.CREATED).entity(favoriteResponse).build();
  }

  @Override
  public Response deleteOrgFavorite(
      String org, String userId, String harnessAccount, String resourceType, String resourceId) {
    try {
      favoritesService.deleteFavorite(harnessAccount, org, null, userId, resourceType, resourceId);
    } catch (InvalidRequestException exception) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(ResponseMessage.builder()
                      .code(ErrorCode.INVALID_REQUEST)
                      .level(Level.ERROR)
                      .message(exception.getMessage())
                      .build())
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getOrgFavorites(String org, String userId, String harnessAccount, String resourceType) {
    if (resourceType != null) {
      List<FavoriteResponse> favoriteResponses = favoritesResourceUtils.toFavoriteResponse(
          favoritesService.getFavorites(harnessAccount, org, null, userId, resourceType));
      return Response.ok().entity(favoriteResponses).build();
    }
    List<FavoriteResponse> favoriteResponses =
        favoritesResourceUtils.toFavoriteResponse(favoritesService.getFavorites(harnessAccount, org, null, userId));
    return Response.ok().entity(favoriteResponses).build();
  }
}
