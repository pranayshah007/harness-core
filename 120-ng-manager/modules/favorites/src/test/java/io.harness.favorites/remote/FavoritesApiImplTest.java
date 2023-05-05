/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.favorites.remote;

import static io.harness.rule.OwnerRule.BOOPESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.favorites.FavoritesResourceUtils;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.FavoriteEntity;
import io.harness.favorites.services.FavoriteService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.FavoriteResponse;

import java.util.Collections;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class FavoritesApiImplTest extends CategoryTest {
  private AccountFavoriteApiImpl accountFavoriteApi;
  private OrgFavoriteApiImpl orgFavoriteApi;
  private ProjectFavoriteApiImpl projectFavoriteApi;
  private FavoritesResourceUtils favoritesResourceUtils;
  @Mock private FavoriteService favoriteService;
  private final String userId = "userId";
  private final String moduleType = "CD";
  private final String accountId = "accountId";
  private final String orgId = "org";
  private final String projectId = "project";
  private final String resourceType_connector = "CONNECTOR";
  private final String resourceType_project = "PROJECT";
  private final String resourceId = "resourceUUID";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    favoritesResourceUtils = new FavoritesResourceUtils();
    accountFavoriteApi = new AccountFavoriteApiImpl(favoriteService, favoritesResourceUtils);
    orgFavoriteApi = new OrgFavoriteApiImpl(favoriteService, favoritesResourceUtils);
    projectFavoriteApi = new ProjectFavoriteApiImpl(favoriteService, favoritesResourceUtils);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateAccountScopedFavorite() {
    FavoriteEntity favoriteEntity = getFavoriteEntity();
    when(favoriteService.createFavorite(anyString(), any(), any(), anyString(), any(), any(), anyString()))
        .thenReturn(favoriteEntity);
    Response accountFavoriteResponse =
        accountFavoriteApi.createAccountFavorite(userId, moduleType, accountId, resourceType_connector, resourceId);
    assertThat(accountFavoriteResponse).isNotNull();
    assertThat(accountFavoriteResponse.getStatus()).isEqualTo(201);
    assertThat(accountFavoriteResponse.getEntity()).isEqualTo(getFavoriteResponse(favoriteEntity));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateOrgScopedFavorite() {
    FavoriteEntity favoriteEntity = getFavoriteEntity();
    favoriteEntity.setOrgIdentifier(orgId);
    when(favoriteService.createFavorite(anyString(), anyString(), any(), anyString(), any(), any(), anyString()))
        .thenReturn(favoriteEntity);
    Response orgFavoriteResponse =
        orgFavoriteApi.createOrgFavorite(orgId, userId, moduleType, accountId, resourceType_connector, resourceId);
    assertThat(orgFavoriteResponse).isNotNull();
    assertThat(orgFavoriteResponse.getStatus()).isEqualTo(201);
    assertThat(orgFavoriteResponse.getEntity()).isEqualTo(getFavoriteResponse(favoriteEntity));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateProjectScopedFavorite() {
    FavoriteEntity favoriteEntity = getFavoriteEntity();
    favoriteEntity.setOrgIdentifier(orgId);
    favoriteEntity.setProjectIdentifier(projectId);
    when(favoriteService.createFavorite(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
        .thenReturn(favoriteEntity);
    Response projectFavoriteResponse = projectFavoriteApi.createProjectFavorite(
        orgId, projectId, userId, moduleType, accountId, resourceType_connector, resourceId);
    assertThat(projectFavoriteResponse).isNotNull();
    assertThat(projectFavoriteResponse.getStatus()).isEqualTo(201);
    assertThat(projectFavoriteResponse.getEntity()).isEqualTo(getFavoriteResponse(favoriteEntity));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetProjectScopedFavoriteWithResourceType() {
    FavoriteEntity favoriteEntity = getFavoriteEntity();
    favoriteEntity.setOrgIdentifier(orgId);
    favoriteEntity.setProjectIdentifier(projectId);
    when(favoriteService.getFavorites(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(Collections.singletonList(favoriteEntity));
    Response projectFavoriteResponse =
        projectFavoriteApi.getProjectFavorite(orgId, projectId, userId, accountId, resourceType_connector);
    assertThat(projectFavoriteResponse).isNotNull();
    assertThat(projectFavoriteResponse.getStatus()).isEqualTo(200);
    assertThat(projectFavoriteResponse.getEntity())
        .isEqualTo(Collections.singletonList(getFavoriteResponse(favoriteEntity)));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetAllAccountScopedFavoriteOfUser() {
    FavoriteEntity favoriteEntity = getFavoriteEntity();
    when(favoriteService.getFavorites(anyString(), any(), any(), anyString()))
        .thenReturn(Collections.singletonList(favoriteEntity));
    Response projectFavoriteResponse = accountFavoriteApi.getAccountFavorite(userId, accountId, null);
    assertThat(projectFavoriteResponse).isNotNull();
    assertThat(projectFavoriteResponse.getStatus()).isEqualTo(200);
    assertThat(projectFavoriteResponse.getEntity())
        .isEqualTo(Collections.singletonList(getFavoriteResponse(favoriteEntity)));
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testDeleteAccountScopedFavorite() {
    when(favoriteService.deleteFavorite(anyString(), any(), any(), anyString(), anyString())).thenReturn(true);
    Response deleteAccountFavorite = accountFavoriteApi.deleteAccountFavorite(userId, accountId, resourceId);
    assertThat(deleteAccountFavorite).isNotNull();
    assertThat(deleteAccountFavorite.getStatus()).isEqualTo(204);
    assertThat(deleteAccountFavorite.getEntity()).isNull();
  }

  private FavoriteEntity getFavoriteEntity() {
    return FavoriteEntity.builder()
        .accountIdentifier(accountId)
        .resourceIdentifier(resourceId)
        .resourceType(ResourceType.CONNECTOR)
        .module(ModuleType.CD)
        .userIdentifier(userId)
        .build();
  }

  private FavoriteResponse getFavoriteResponse(FavoriteEntity favoriteEntity) {
    return favoritesResourceUtils.toFavoriteResponse(favoriteEntity);
  }
}
