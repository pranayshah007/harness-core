/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.utils;

import static io.harness.rule.OwnerRule.BOOPESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.FavoriteDTO;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;
import io.harness.spec.server.ng.v1.model.FavoritesResourceType;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class FavoritesResourceUtilsTest extends CategoryTest {
  private FavoritesResourceUtils favoritesResourceUtils;
  private final String userId = "userId";
  private final String accountId = "accountId";
  private final String orgId = "org";
  private final String projectId = "project";
  private final String resourceId = "resourceUUID";
  private final long createdAt = 1234567890;
  private Favorite favorite;

  private FavoriteDTO favoriteDTO;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    favoritesResourceUtils = new FavoritesResourceUtils();
    favorite = Favorite.builder()
                   .accountIdentifier(accountId)
                   .orgIdentifier(orgId)
                   .projectIdentifier(projectId)
                   .userIdentifier(userId)
                   .module(ModuleType.CD)
                   .resourceType(ResourceType.CONNECTOR)
                   .resourceIdentifier(resourceId)
                   .created(createdAt)
                   .build();
    favoriteDTO = new FavoriteDTO()
                      .org(orgId)
                      .project(projectId)
                      .userId(userId)
                      .module(io.harness.spec.server.ng.v1.model.ModuleType.CD)
                      .resourceType(FavoritesResourceType.CONNECTOR)
                      .resourceId(resourceId);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testToFavoriteEntity() {
    favorite.setCreated(null);
    Favorite favoriteEntity = favoritesResourceUtils.toFavoriteEntity(favoriteDTO, accountId);
    assertThat(favoriteEntity).isNotNull();
    assertThat(favoriteEntity).isEqualTo(favorite);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testToFavoriteResponse() {
    FavoriteResponse favoriteResponse = favoritesResourceUtils.toFavoriteResponse(favorite);
    assertThat(favoriteResponse.getCreated()).isNotNull();
    assertThat(favoriteResponse.getFavorite()).isEqualTo(favoriteDTO);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testToFavoriteResponseList() {
    List<FavoriteResponse> favoriteResponseList =
        favoritesResourceUtils.toFavoriteResponse(Collections.singletonList(favorite));
    assertThat(favoriteResponseList.size()).isEqualTo(1);
    assertThat(favoriteResponseList.get(0).getFavorite()).isEqualTo(favoriteDTO);
  }
  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testToFavoritesResponseList() {
    String projectUUID = "projectUUID";
    Favorite favorite2 = Favorite.builder()
                             .accountIdentifier(accountId)
                             .orgIdentifier(orgId)
                             .projectIdentifier(projectId)
                             .userIdentifier(userId)
                             .module(ModuleType.CI)
                             .resourceType(ResourceType.PROJECT)
                             .resourceIdentifier(projectUUID)
                             .created(createdAt)
                             .build();
    FavoriteDTO favorite2DTO = new FavoriteDTO()
                                   .org(orgId)
                                   .project(projectId)
                                   .userId(userId)
                                   .module(io.harness.spec.server.ng.v1.model.ModuleType.CI)
                                   .resourceType(FavoritesResourceType.PROJECT)
                                   .resourceId(projectUUID);
    List<FavoriteResponse> favoriteResponseList =
        favoritesResourceUtils.toFavoriteResponse(List.of(favorite, favorite2));
    assertThat(favoriteResponseList).hasSize(2);
    assertThat(favoriteResponseList.get(0).getFavorite()).isEqualTo(favoriteDTO);
    assertThat(favoriteResponseList.get(1).getFavorite()).isEqualTo(favorite2DTO);
  }
}
